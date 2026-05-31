output "elastic_ip" {
  description = "Public IP. Use this for DNS (or the nip.io hostname below)."
  value       = aws_eip.app.public_ip
}

output "nip_io_hostname" {
  description = "Free hostname that resolves to the Elastic IP. Use this as CADDY_DOMAIN."
  value       = "bubbleup.${replace(aws_eip.app.public_ip, ".", "-")}.nip.io"
}

output "instance_id" {
  description = "Pass this to the GitHub deploy workflow as the SSM target."
  value       = aws_instance.app.id
}

output "backup_bucket" {
  description = "S3 bucket where nightly pg_dump backups land."
  value       = aws_s3_bucket.backups.bucket
}

output "github_deploy_role_arn" {
  description = "Set this as the AWS_DEPLOY_ROLE_ARN secret/variable in GitHub Actions."
  value       = aws_iam_role.github_deploy.arn
}

output "ssm_parameter_prefix" {
  description = "Prefix all SecureString secrets live under."
  value       = "/${var.project}/prod/"
}
