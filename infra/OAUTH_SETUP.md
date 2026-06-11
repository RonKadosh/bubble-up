# Google OAuth2 + AWS SES setup

One-time manual setup needed to make the Google sign-in + email-verification
flow work in production.

There are 3 humans-only steps:

1. Create a Google OAuth2 client in Google Cloud Console
2. Verify a sender identity in AWS SES
3. Drop the new secrets into SSM Parameter Store

After that, everything else (IAM permissions, code, env wiring) is handled by
Terraform + the deploy workflow.

## Canonical production host

Bubble.up production uses `https://bubbleup.online` as the single canonical
origin.

- `https://bubbleup.online` is the app host used for Google OAuth and email links.
- `https://www.bubbleup.online` may resolve too, but it should only redirect to
  `https://bubbleup.online`.
- Google Cloud should allow both hosts if `www` still resolves, but users
  should always start the OAuth flow from the root domain.

---

## 1. Google OAuth2 client (5 minutes)

1. Open [Google Cloud Console](https://console.cloud.google.com/) and sign in
   with the Google account that will manage the app.
2. Create a project named `Bubble.up`.
3. Open **OAuth consent screen**.
   - User type: `External`
   - App name: `Bubble.up`
   - User support email: your email
   - Application home page: `https://bubbleup.online`
   - Authorized domains: `bubbleup.online`
   - Developer contact email: your email
   - Save and continue through scopes. No extra scopes are needed; the app only
     requests `email` and `profile`.
   - Add yourself as a test user while the app is still in testing mode.
4. Open **Credentials** and create an **OAuth client ID**.
   - Application type: `Web application`
   - Name: `bubbleup-web`
   - Authorized JavaScript origins:
     - `http://localhost:3000`
     - `https://bubbleup.online`
     - `https://www.bubbleup.online` (optional alias, if it still resolves)
   - Authorized redirect URIs:
     - `http://localhost:3000/login/oauth2/code/google`
     - `http://localhost:8080/login/oauth2/code/google`
     - `https://bubbleup.online/login/oauth2/code/google`
     - `https://www.bubbleup.online/login/oauth2/code/google` (optional alias,
       if `www` still resolves)
5. Copy the generated **Client ID** and **Client secret**. You will paste them
   into SSM in step 3.

---

## 2. AWS SES sender identity (5 minutes)

We use SES from the EC2 box to send the verification emails. Free tier covers
62,000 messages/month when sending from EC2.

### Verify a sender email

1. Open the SES verified identities page in `us-east-1`.
2. Click **Create identity** and choose **Email address**.
3. Enter a sender, for example `noreply@your-real-domain.com` for production or
   a verified Gmail address for testing.
4. Create the identity.
5. Open the inbox of that address and click the AWS verification link.
6. Confirm the identity status is **Verified** in SES.

### Optional but recommended for launch: request production access

While SES is in the sandbox you can only send to verified recipient addresses.
That is enough for early testing, but not for launch.

1. Open the SES account page in `us-east-1`.
2. Click **Request production access**.
3. Mail type: `Transactional`
4. Website: `https://bubbleup.online`
5. Use case description:

   > Bubble.up is an Israeli academic study collaboration platform for
   > university students. We send email verification messages to users who sign
   > up with an academic `.ac.il` inbox. Messages are transactional only. Volume
   > is expected to stay well below 1,000 messages per month.

6. Submit the request.

Until production access is approved, only explicitly verified recipient emails
can receive the verification messages.

---

## 3. SSM secrets (1 minute)

Paste the values from steps 1 and 2 into Parameter Store:

```powershell
# Google OAuth
aws ssm put-parameter --name /bubbleup/prod/GOOGLE_OAUTH_CLIENT_ID `
  --type SecureString --value "<paste-client-id>.apps.googleusercontent.com" --overwrite

aws ssm put-parameter --name /bubbleup/prod/GOOGLE_OAUTH_CLIENT_SECRET `
  --type SecureString --value "GOCSPX-<paste-secret>" --overwrite

# SES sender
aws ssm put-parameter --name /bubbleup/prod/MAIL_FROM_ADDRESS `
  --type SecureString --value "noreply@your-domain.com" --overwrite

# SES region
aws ssm put-parameter --name /bubbleup/prod/AWS_SES_REGION `
  --type String --value "us-east-1" --overwrite
```

Verify:

```powershell
aws ssm get-parameters-by-path --path /bubbleup/prod --query "Parameters[].Name" --output text
```

You should see the four names above alongside the existing JWT, DB, JITSI, and
GHCR entries.

Also sanity-check that `/bubbleup/prod/GOOGLE_OAUTH_CLIENT_ID` and
`/bubbleup/prod/GOOGLE_OAUTH_CLIENT_SECRET` belong to the same Google OAuth
client you just edited. A stale client ID/secret pair can still break prod
Google sign-in even when the browser is on the right domain.

---

## 4. Local dev `.env` for `docker compose up`

Add the same values to your local `.env` (gitignored) so local dev works:

```env
GOOGLE_OAUTH_CLIENT_ID=<paste>.apps.googleusercontent.com
GOOGLE_OAUTH_CLIENT_SECRET=GOCSPX-<paste>

MAIL_FROM_ADDRESS=your-verified-email@gmail.com
AWS_SES_REGION=us-east-1

AWS_ACCESS_KEY_ID=AKIA...
AWS_SECRET_ACCESS_KEY=...
```

In production, the EC2 instance role is granted SES permissions via Terraform,
so no AWS access keys are needed on the box.

---

## When something does not work

| Symptom | Probable cause |
| --- | --- |
| Google sign-in returns `redirect_uri_mismatch` | The Google OAuth client does not exactly include the callback URL. Re-check `http://localhost:3000/login/oauth2/code/google`, `http://localhost:8080/login/oauth2/code/google`, and `https://bubbleup.online/login/oauth2/code/google`. If `www` still resolves, add `https://www.bubbleup.online/login/oauth2/code/google` too. |
| Prod login still breaks after updating Google Cloud | The SSM `GOOGLE_OAUTH_CLIENT_ID` / `GOOGLE_OAUTH_CLIENT_SECRET` do not belong to the same OAuth client you edited. |
| Verification email never arrives | SES is still sandboxed and the recipient is not verified, or `MAIL_FROM_ADDRESS` is wrong. |
| Prod EC2 logs `AccessDenied: ses:SendEmail` | `terraform apply` was not run after the SES policy was added. Re-apply Terraform. |
