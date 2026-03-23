terraform {
  backend "s3" {
    bucket         = "supporthub-terraform-state"
    key            = "staging/terraform.tfstate"
    region         = "ap-south-1"
    dynamodb_table = "supporthub-terraform-locks"
    encrypt        = true
  }
}
