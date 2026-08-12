variable "region" {
  description = "AWS region. us-east-1 has the largest free tier surface."
  type        = string
  default     = "us-east-1"
}

variable "project" {
  description = "Short name used as prefix for resources."
  type        = string
  default     = "bubbleup"
}

variable "github_repo" {
  description = "GitHub owner/repo for OIDC trust + GHCR image path."
  type        = string
  default     = "RonKadosh/bubble-up"
}

variable "instance_type" {
  description = "EC2 instance type. t3.micro is free tier for 12 months on new accounts."
  type        = string
  default     = "t3.micro"
}

variable "data_volume_size_gb" {
  description = "Size of the dedicated EBS data volume (postgres + uploaded files + caddy certs)."
  type        = number
  default     = 8
}

variable "budget_email" {
  description = "Email to notify when monthly spend approaches the budget cap."
  type        = string
}

variable "monthly_budget_usd" {
  description = "AWS Budgets monthly cap. Alarms fire at 80% actual + 100% forecasted."
  type        = number
  default     = 5
}
