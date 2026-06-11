# Google OAuth2 + AWS SES setup

One-time manual setup needed to make the Google sign-in + email-verification flow work. Code changes are on the `feature/feature-oauth-support` branch.

There are 3 humans-only steps:

1. **Create a Google OAuth2 client** in Google Cloud Console
2. **Verify a sender identity in AWS SES**
3. **Drop the new secrets into SSM Parameter Store**

After that, everything else (IAM permissions, code, env wiring) is handled by Terraform + the deploy workflow.

---

## 1. Google OAuth2 client (5 minutes)

1. Open **https://console.cloud.google.com/**. Sign in with whatever Google account will manage the app.
2. Create a new project: **"Select a project"** dropdown → **New project** → name it `Bubble.up`.
3. In the search bar, type **"OAuth consent screen"** → open it.
   - **User type**: External → Create
   - **App name**: `Bubble.up`
   - **User support email**: your email
   - **App domain → Application home page**: `https://bubbleup.100-50-61-243.nip.io` (or your real domain when you have one)
   - **Authorized domains**: add `nip.io` (or your real domain)
   - **Developer contact email**: same as user support
   - **Save and continue** through the scopes screen (no extra scopes needed — we only ask for `email` and `profile`).
   - Add yourself as a **Test user** so you can sign in while the app is in "Testing" status.
4. In the search bar, type **"Credentials"** → open it.
   - Click **+ Create credentials** → **OAuth client ID**.
   - **Application type**: Web application
   - **Name**: `bubbleup-web`
   - **Authorized JavaScript origins**:
     - `http://localhost:3000` (dev frontend)
     - `https://bubbleup.100-50-61-243.nip.io` (prod)
   - **Authorized redirect URIs**:
     - `http://localhost:3000/login/oauth2/code/google` (dev via frontend proxy / docker-compose)
     - `http://localhost:8080/login/oauth2/code/google` (dev when hitting backend directly)
     - `https://bubbleup.100-50-61-243.nip.io/login/oauth2/code/google` (prod, through Caddy)
   - **Create** → you'll get a **Client ID** (`<random>.apps.googleusercontent.com`) and **Client secret** (`GOCSPX-<random>`). **Copy both — the secret is only shown once**.

You'll paste these into SSM in step 3.

---

## 2. AWS SES sender identity (5 minutes)

We use SES from the EC2 box to send the verification emails. Free tier covers **62,000 messages/month** when sending from EC2 — way more than we'll need.

### Verify a sender email
1. Open https://us-east-1.console.aws.amazon.com/ses/home?region=us-east-1#/verified-identities
2. Click **Create identity** → choose **Email address**
3. Enter a sender, e.g. `noreply@your-real-domain.com` if you have a domain, OR your personal Gmail for testing (e.g. `bubbleup.notifications@gmail.com`)
4. **Create identity**
5. Open the inbox of that address → click the AWS verification link
6. Confirm in the SES console that the identity status is **Verified**

### (Optional but recommended for launch) Request production access

While in the SES **sandbox** you can only send to *verified* addresses — fine for testing with you + a couple of teammates. To send to anyone:

1. https://us-east-1.console.aws.amazon.com/ses/home?region=us-east-1#/account
2. Click **Request production access**
3. Mail type: Transactional
4. Website: `https://bubbleup.100-50-61-243.nip.io`
5. Use case description: copy-paste:
   > Bubble.up is a Israeli academic study collaboration platform for university students. We send email verification messages to users who sign up using a `.ac.il` email address but log in with a personal Google account, so we can confirm they actually own the academic mailbox. Volume is expected to be < 1,000 messages/month. Compliance: only legitimate university members can sign up; all messages are transactional (verification only, no marketing).
6. **Submit request** → usually approved in 24h.

Until you do this, only people whose emails you've explicitly verified can receive emails — sign yourself + Ron + a test alias in step 1 and you'll be unblocked for early testing.

---

## 3. SSM secrets (1 minute)

Paste the values from steps 1 + 2 into Parameter Store. You can do this from PowerShell on your machine (your AWS CLI is already configured):

```powershell
# From step 1 (Google):
aws ssm put-parameter --name /bubbleup/prod/GOOGLE_OAUTH_CLIENT_ID `
  --type SecureString --value "<paste-client-id>.apps.googleusercontent.com" --overwrite

aws ssm put-parameter --name /bubbleup/prod/GOOGLE_OAUTH_CLIENT_SECRET `
  --type SecureString --value "GOCSPX-<paste-secret>" --overwrite

# From step 2 (SES) — must be a verified identity:
aws ssm put-parameter --name /bubbleup/prod/MAIL_FROM_ADDRESS `
  --type SecureString --value "noreply@your-domain.com" --overwrite

# SES region for the SDK (same as our resources):
aws ssm put-parameter --name /bubbleup/prod/AWS_SES_REGION `
  --type String --value "us-east-1" --overwrite
```

Verify:
```powershell
aws ssm get-parameters-by-path --path /bubbleup/prod --query "Parameters[].Name" --output text
```
You should see the new 4 names alongside the existing JWT/DB/JITSI/GHCR ones.

---

## 4. Local dev — `.env` for `docker-compose up`

Add the same values to your local `.env` (gitignored) so local dev works:

```env
# Google OAuth (from step 1 — same values, OR a separate "dev" OAuth client if you prefer)
GOOGLE_OAUTH_CLIENT_ID=<paste>.apps.googleusercontent.com
GOOGLE_OAUTH_CLIENT_SECRET=GOCSPX-<paste>

# SES sender — for local dev you can use a personal verified email
MAIL_FROM_ADDRESS=your-verified-email@gmail.com
AWS_SES_REGION=us-east-1

# AWS creds for the SES SDK in dev (use your IAM user, not root)
AWS_ACCESS_KEY_ID=AKIA...
AWS_SECRET_ACCESS_KEY=...
```

In prod, the EC2 instance role is auto-extended with `ses:SendEmail` permission (via Terraform in `infra/iam.tf`), so no AWS keys are needed on the box — only locally for `docker compose up` dev workflow.

---

## When something doesn't work

| Symptom | Probable cause |
|---|---|
| Google sign-in returns "redirect_uri_mismatch" | The `Authorized redirect URIs` in Google Cloud Console doesn't exactly match. Trailing slashes matter. Re-check `http://localhost:3000/login/oauth2/code/google` for docker-compose/frontend-proxy dev, `http://localhost:8080/login/oauth2/code/google` for direct-backend dev, and the prod URL. |
| Sign-in works but the app says "not a recognized academic email" | The Google email isn't a `.ac.il` address. You'll then be prompted for a secondary academic email — the verification link goes via SES. |
| Verification email never arrives | (a) SES sandbox + recipient not verified, OR (b) wrong `MAIL_FROM_ADDRESS`. Look in SES → "Sending statistics" for bounces. |
| Prod EC2 logs `AccessDenied: ses:SendEmail` | `terraform apply` wasn't run after the SES policy was added. Re-apply. |
