data "aws_ssm_parameter" "al2023_ami" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

locals {
  # AL2023 SSM parameter sometimes returns the value with quotes; trim if needed.
  al2023_ami_id = data.aws_ssm_parameter.al2023_ami.value

  # Render user-data with the values it needs to know at boot.
  user_data = templatefile("${path.module}/user-data.sh.tftpl", {
    backup_bucket = aws_s3_bucket.backups.bucket
    region        = var.region
    project       = var.project
    github_repo   = var.github_repo
  })
}

resource "aws_instance" "app" {
  ami                         = local.al2023_ami_id
  instance_type               = var.instance_type
  iam_instance_profile        = aws_iam_instance_profile.ec2.name
  vpc_security_group_ids      = [aws_security_group.app.id]
  subnet_id                   = data.aws_subnets.default.ids[0]
  associate_public_ip_address = true
  user_data                   = local.user_data
  user_data_replace_on_change = false

  root_block_device {
    volume_size           = 8
    volume_type           = "gp3"
    encrypted             = true
    delete_on_termination = true
  }

  metadata_options {
    http_tokens   = "required" # IMDSv2 only
    http_endpoint = "enabled"
  }

  tags = {
    Name = "${var.project}-app"
  }

  lifecycle {
    ignore_changes = [ami] # rebuilds shouldn't auto-replace the box
  }
}

# Dedicated EBS volume for /data — survives instance replacement.
resource "aws_ebs_volume" "data" {
  availability_zone = aws_instance.app.availability_zone
  size              = var.data_volume_size_gb
  type              = "gp3"
  encrypted         = true

  tags = {
    Name = "${var.project}-data"
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_volume_attachment" "data" {
  device_name  = "/dev/sdf"
  volume_id    = aws_ebs_volume.data.id
  instance_id  = aws_instance.app.id
  force_detach = false
  stop_instance_before_detaching = false
}

resource "aws_eip" "app" {
  instance = aws_instance.app.id
  domain   = "vpc"
}
