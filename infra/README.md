# Bubble.up — AWS infra

One-shot Terraform that stands up the entire prod stack on AWS free tier:

| Resource | Why |
| --- | --- |
| EC2 `t3.micro` (Amazon Linux 2023) | Runs Docker, the whole compose stack | 
| Elastic IP | Stable public address — free while attached to a running instance |
| EBS `gp3` 8 GB (root) + 8 GB (`/data`) | App + persistent data on a separate volume that survives instance replacement |
| Security group | 80/443 open, **no port 22** |
| IAM instance profile | SSM-managed, reads `/bubbleup/prod/*` params, writes nightly backups to S3 |
| S3 backup bucket | `pg_dump` lands here nightly. 30-day lifecycle, AES-256, public access blocked |
| GitHub OIDC provider + deploy role | Lets `.github/workflows/deploy.yml` assume a role without long-lived keys |
| AWS Budgets `$5/mo` | Email alarm at 80 % actual + 100 % forecasted |

Region is `us-east-1` by default. Override in `terraform.tfvars` if you need.

## One-time prerequisites

1. **AWS account.** Add a payment method even if you stay inside free tier — AWS blocks resource creation otherwise. Verify the account is < 12 months old to actually hit free tier on EC2.
2. **CLI tools** (installable from PowerShell):
   ```powershell
   winget install --id Hashicorp.Terraform -e
   winget install --id Amazon.AWSCLI -e
   ```
3. **An IAM user with `AdministratorAccess`** (just for the bootstrap — tighten later). Create an access key pair, then in PowerShell:
   ```powershell
   aws configure
   # AWS Access Key ID:     <paste>
   # AWS Secret Access Key: <paste>
   # Default region:        us-east-1
   # Default output:        json
   ```
4. **GitHub repo settings → Secrets and variables → Actions → New variable**
   - `AWS_DEPLOY_ROLE_ARN` ← will be filled in from `terraform output github_deploy_role_arn` after step 1 below.
   - `AWS_REGION` ← `us-east-1`
   - `EC2_INSTANCE_ID` ← from `terraform output instance_id`
   - `CADDY_DOMAIN` ← from `terraform output nip_io_hostname` (or your real domain)

## 1. Stand up the infra

```powershell
cd infra
terraform init
terraform plan -out tfplan
terraform apply tfplan
```

After apply, capture the outputs:

```powershell
terraform output
```

You'll get the Elastic IP, the `nip.io` hostname (e.g. `bubbleup.3-92-11-7.nip.io`), the instance ID, the backup bucket name, and the GitHub deploy role ARN. Plug the relevant values into GitHub Actions variables (step 4 above).

## 2. Put the production secrets in SSM Parameter Store

Run these once. **Replace the bracketed values** — pull the Jitsi ones from the existing local `.env` at the repo root.

```powershell
# Generate a strong JWT secret (64+ chars, base64 from /dev/urandom equivalent)
$jwt = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 64 | ForEach-Object {[char]$_})

# Generate a strong DB password
$dbp = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 32 | ForEach-Object {[char]$_})

aws ssm put-parameter --name /bubbleup/prod/JWT_SECRET --type SecureString --value $jwt --overwrite
aws ssm put-parameter --name /bubbleup/prod/DB_PASSWORD --type SecureString --value $dbp --overwrite

aws ssm put-parameter --name /bubbleup/prod/APP_JITSI_APP_ID --type SecureString --value "<from local .env>" --overwrite
aws ssm put-parameter --name /bubbleup/prod/APP_JITSI_KID --type SecureString --value "<from local .env>" --overwrite

# PEM has newlines — read it from a file to preserve them.
aws ssm put-parameter --name /bubbleup/prod/APP_JITSI_PRIVATE_KEY_PEM --type SecureString --value (Get-Content jitsi-private.pem -Raw) --overwrite
```

Store `jwt`/`dbp` somewhere safe if you want disaster recovery; otherwise they only need to exist in SSM.

## 3. Ship a release

Cut a GitHub Release on a `v*.*.*` tag (e.g. `v0.1.0`):

```bash
git tag v0.1.0
git push origin v0.1.0
```

`ci.yml` builds + pushes images to GHCR; `deploy.yml` then sends an SSM Run Command to the EC2 box that fetches secrets, writes `/opt/bubbleup/.env`, pulls the new images, and brings the stack up. Caddy gets a Let's Encrypt cert automatically (the `nip.io` hostname satisfies the HTTP-01 challenge over port 80 once DNS resolves, which is instant for nip.io).

## 4. Verify

```powershell
# Should return 200 once Caddy has its cert (~30s after first deploy)
curl https://<nip-io-hostname>/api/auth/health
```

Sign up at `https://<nip-io-hostname>/` — there are no demo accounts in prod (seed is disabled).

## Tearing down

```powershell
terraform destroy
```

The data EBS volume has `prevent_destroy = true` for safety — delete it manually in the console after taking a backup, or remove the lifecycle block first.
