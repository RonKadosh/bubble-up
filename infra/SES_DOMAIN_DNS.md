# SES domain identity DNS records — bubbleup.online

Add these to the DNS for **bubbleup.online** (same place you set the A-record
→ 100.50.61.243). All three are **CNAME** records. Most DNS UIs let you enter
just the host label (the part before `.bubbleup.online`); some want the full
name. Both forms are shown.

## 1. DKIM (REQUIRED — this is what authenticates the mail)

| Type | Name (host) | Value |
|------|-------------|-------|
| CNAME | `fja2ijyagwtj4vbk5yrolaei4yr3ynif._domainkey` | `fja2ijyagwtj4vbk5yrolaei4yr3ynif.dkim.amazonses.com` |
| CNAME | `ocvpwwwrk62lt6czqe35lqenjmlefbxk._domainkey` | `ocvpwwwrk62lt6czqe35lqenjmlefbxk.dkim.amazonses.com` |
| CNAME | `gayrazvk6x4fpnpq3vrikbv5kqkzqy6f._domainkey` | `gayrazvk6x4fpnpq3vrikbv5kqkzqy6f.dkim.amazonses.com` |

Full names (if your DNS UI requires the whole thing):
- `fja2ijyagwtj4vbk5yrolaei4yr3ynif._domainkey.bubbleup.online`
- `ocvpwwwrk62lt6czqe35lqenjmlefbxk._domainkey.bubbleup.online`
- `gayrazvk6x4fpnpq3vrikbv5kqkzqy6f._domainkey.bubbleup.online`

> Do NOT add a trailing dot or `bubbleup.online` to the *value* — it's literally
> `<token>.dkim.amazonses.com`.

## 2. DMARC (RECOMMENDED — tells receivers to trust authenticated mail)

| Type | Name (host) | Value |
|------|-------------|-------|
| TXT | `_dmarc` | `v=DMARC1; p=none; rua=mailto:dmarc@example.com` |

(`p=none` = monitor only, safe to start. Tighten to `quarantine` later.)

## 3. SPF (optional, improves alignment if you later add a custom MAIL FROM)

Not needed for DKIM-based DMARC pass. Skip for now.

---

## After adding the records

SES auto-checks every few minutes. Verification usually completes within
15–60 min of DNS propagation. Check status:

```
aws sesv2 get-email-identity --region us-east-1 --email-identity bubbleup.online --query "{Verified:VerifiedForSendingStatus, DKIM:DkimAttributes.Status}"
```

When `Verified: true` + `DKIM: SUCCESS`:
1. Switch the sender to `noreply@bubbleup.online`:
   - `aws ssm put-parameter --name /bubbleup/prod/MAIL_FROM_ADDRESS --type SecureString --value noreply@bubbleup.online --overwrite`
   - update local `.env` MAIL_FROM_ADDRESS the same
   - restart backend
2. Reply to the AWS production-access case (#178110867500842) — now you have a
   verified domain identity, which is what they asked for.
