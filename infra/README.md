# Bubble.up - AWS infra

One-shot Terraform that stands up the production stack on AWS free tier.

| Resource | Why |
| --- | --- |
| EC2 `t3.micro` (Amazon Linux 2023) | Runs Docker and the compose stack |
| Elastic IP | Stable public address |
| EBS `gp3` 8 GB root + 8 GB `/data` | App files plus persistent data |
| Security group | Opens 80/443, no SSH |
| IAM instance profile | Lets the box use SSM, read secrets, and write backups |
| S3 backup bucket | Stores nightly `pg_dump` backups |
| GitHub OIDC provider + deploy role | Lets GitHub Actions deploy without long-lived AWS keys |
| AWS Budget | Basic monthly cost guardrail |

Region is `us-east-1` by default. Override it in `terraform.tfvars` if needed.

## One-time prerequisites

1. AWS account with billing enabled.
2. CLI tools:

   ```powershell
   winget install --id Hashicorp.Terraform -e
   winget install --id Amazon.AWSCLI -e
   ```

3. An IAM user with enough permissions to bootstrap the infrastructure.

   ```powershell
   aws configure
   ```

4. GitHub Actions variables:
   - `AWS_DEPLOY_ROLE_ARN` from `terraform output github_deploy_role_arn`
   - `AWS_REGION=us-east-1`
   - `EC2_INSTANCE_ID` from `terraform output instance_id`
   - `CADDY_DOMAIN=bubbleup.online`

Use the `nip.io` hostname only as a temporary bootstrap fallback. Real
production should use `bubbleup.online` as the canonical host.

## 1. Stand up the infra

```powershell
cd infra
terraform init
terraform plan -out tfplan
terraform apply tfplan
```

Then inspect the outputs:

```powershell
terraform output
```

Terraform will give you the Elastic IP, temporary `nip.io` hostname, instance
ID, backup bucket, and GitHub deploy role ARN. For a real launch, point
`bubbleup.online` at the Elastic IP and set `CADDY_DOMAIN=bubbleup.online` in
GitHub Actions.

## 2. Put production secrets into SSM Parameter Store

Run these once. Replace the placeholder values and pull the JITSI values from
the local repo `.env`.

```powershell
$jwt = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 64 | ForEach-Object {[char]$_})
$dbp = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 32 | ForEach-Object {[char]$_})

aws ssm put-parameter --name /bubbleup/prod/JWT_SECRET --type SecureString --value $jwt --overwrite
aws ssm put-parameter --name /bubbleup/prod/DB_PASSWORD --type SecureString --value $dbp --overwrite

aws ssm put-parameter --name /bubbleup/prod/APP_JITSI_APP_ID --type SecureString --value "<from local .env>" --overwrite
aws ssm put-parameter --name /bubbleup/prod/APP_JITSI_KID --type SecureString --value "<from local .env>" --overwrite
aws ssm put-parameter --name /bubbleup/prod/APP_JITSI_PRIVATE_KEY_PEM --type SecureString --value (Get-Content jitsi-private.pem -Raw) --overwrite
```

Keep the generated JWT and DB password somewhere safe if you want disaster
recovery outside SSM.

## 3. Ship a release

Create and push a release tag in `vX.Y.Z` format. The workflows in this repo do
not support tag names like `1.9v`, `1.9`, or `v1.9`.

```bash
git tag v1.9.0
git push origin v1.9.0
```

Then publish the GitHub Release for that same tag.

`ci.yml` builds and publishes the backend/frontend images to GHCR. `deploy.yml`
then sends an SSM Run Command to the EC2 box, writes `/opt/bubbleup/.env`,
pulls the new images, and restarts the stack.

Production should serve:

- `https://bubbleup.online` as the canonical app host
- `https://www.bubbleup.online` only as a redirect alias to the root domain

## 4. Verify

```powershell
curl https://bubbleup.online/api/actuator/health
```

Then visit `https://bubbleup.online/` and verify:

- the app loads
- `www.bubbleup.online` redirects to the root host
- Google sign-in reaches the callback without `redirect_uri_mismatch`

## Demo deploy (demo.bubbleup.online)

The public interactive demo runs **separately** from this AWS stack, on its own VPS,
with its own Postgres — so demo traffic and the disposable per-session worlds can never
touch the real users' database or compete for the `t3.micro`'s RAM. It is **not** part of
the Terraform above and **not** deployed by `deploy.yml` (which is AWS/SSM-only).

Pieces in this repo:

- `docker-compose.demo.yml` — demo stack (own DB, `APP_DEMO_ENABLED=true`, the
  `bubble-up-frontend-demo` image, shared backend image).
- `Caddyfile.demo` — like the prod Caddyfile but without the `www.` redirect.
- `.github/workflows/deploy-demo.yml` — SSH deploy, manual `workflow_dispatch`.
- `ci.yml` builds and pushes `bubble-up-frontend-demo` (built with `VITE_DEMO_MODE=true`)
  on every `vX.Y.Z` tag, alongside the prod images.

### One-time setup

1. **VPS**: install Docker Engine + the compose plugin; open firewall ports 80 and 443;
   add the deploy key's public half to `~/.ssh/authorized_keys` for the ssh user (which
   must be in the `docker` group); and pre-create the data dirs:

   ```bash
   sudo mkdir -p /opt/bubbleup-demo /data/postgres /data/files /data/caddy/data /data/caddy/config
   ```

2. **DNS**: add an `A` record `demo` → the VPS public IP at the bubbleup.online DNS
   provider. Leave the root `@` and `www` records (pointing at the EC2 Elastic IP)
   untouched — the real app is unaffected.

3. **GitHub secrets** (Settings → Secrets and variables → Actions):
   `DEMO_SSH_HOST`, `DEMO_SSH_USER`, `DEMO_SSH_KEY`, `DEMO_DB_PASSWORD`,
   `DEMO_JWT_SECRET` (generate distinct from prod — same generator as §2 above),
   `GHCR_USER`, `GHCR_PAT` (PAT with `read:packages`).
   Optional **variables**: `DEMO_CADDY_DOMAIN` (defaults to `demo.bubbleup.online`),
   `DEMO_ACME_EMAIL`.

### Ship a demo release

Cut a release tag as in §3 (`ci.yml` builds + pushes all three images), then run the
**deploy-demo** workflow manually (Actions → deploy-demo → Run workflow) with the image
tag (e.g. `0.1.0` or `latest`). It SSHes into the VPS, writes `/opt/bubbleup-demo/.env`,
pulls the images, and restarts the stack.

### Verify

```bash
curl https://demo.bubbleup.online/api/actuator/health
```

Then visit `https://demo.bubbleup.online/` → it redirects to `/demo` → **Start demo**
builds a fresh isolated world, auto-logs in the guest, and launches the guided tour.
Confirm `https://bubbleup.online` is unchanged.

## Tearing down

```powershell
terraform destroy
```

The data EBS volume has `prevent_destroy = true` for safety. Delete it manually
after taking a backup, or remove that lifecycle block first.
