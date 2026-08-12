# BookTown 클라우드 보강 로드맵 → GitHub 이슈 일괄 생성
# 사전 조건: gh auth login 완료 (repo 권한), Sungw0o/Booktown-V2 접근 가능
# 실행: 이 파일 위치에서  ->  pwsh ./create-issues.ps1
#   미리보기만:            ->  pwsh ./create-issues.ps1 -DryRun

param(
    [string]$Repo = "Sungw0o/Booktown-V2",
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$OutputEncoding = [System.Text.Encoding]::UTF8
$dir = $PSScriptRoot

# gh 설치/인증 확인
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Write-Error "gh(GitHub CLI)가 없습니다. 새 터미널에서 gh --version 확인 후 다시 실행하세요."
}
gh auth status 1>$null 2>$null
if ($LASTEXITCODE -ne 0) { Write-Error "gh 인증이 필요합니다: gh auth login" }

# 1) 라벨 보장 (있으면 --force 로 갱신)
$labels = @(
    @{ name = "cloud";       color = "1D76DB"; desc = "클라우드/인프라 로드맵" },
    @{ name = "infra";       color = "0E8A16"; desc = "인프라·배포" },
    @{ name = "enhancement"; color = "A2EEEF"; desc = "기능 추가" },
    @{ name = "phase-1";     color = "5319E7"; desc = "로드맵 Phase 1(신뢰도)" },
    @{ name = "phase-2";     color = "B60205"; desc = "로드맵 Phase 2(차별화)" },
    @{ name = "phase-3";     color = "FBCA04"; desc = "로드맵 Phase 3(고난도)" }
)
foreach ($l in $labels) {
    if ($DryRun) { Write-Host "[dry-run] label: $($l.name)"; continue }
    gh label create $l.name --color $l.color --description $l.desc --force --repo $Repo | Out-Null
}

# 2) 이슈 정의
$issues = @(
    @{ title = "🚀 infra: Terraform으로 AWS 기본 리소스 IaC 코드화";              file = "issue-1-terraform-iac.md";    labels = "cloud,infra,phase-1" },
    @{ title = "🚀 infra: 단일 EC2 + Nginx upstream 기반 Blue-Green 무중단 배포"; file = "issue-2-nginx-blue-green.md";  labels = "cloud,infra,phase-1" },
    @{ title = "✨ feat: Lambda 사이드 파이프라인 (S3 이벤트/EventBridge)";        file = "issue-3-lambda-pipeline.md";  labels = "cloud,enhancement,phase-2" },
    @{ title = "🚀 infra: 3-tier + ASG Terraform 레퍼런스 아키텍처";              file = "issue-4-3tier-asg.md";        labels = "cloud,infra,phase-3" }
)

# 3) 생성
foreach ($i in $issues) {
    $body = Join-Path $dir $i.file
    if (-not (Test-Path $body)) { Write-Error "본문 파일 없음: $body" }
    if ($DryRun) {
        Write-Host "[dry-run] issue: $($i.title)  (labels: $($i.labels))"
        continue
    }
    $url = gh issue create --repo $Repo --title $i.title --body-file $body --label $i.labels
    Write-Host "생성됨: $url"
}

Write-Host "`n완료. 이슈 목록: gh issue list --repo $Repo --label cloud"
