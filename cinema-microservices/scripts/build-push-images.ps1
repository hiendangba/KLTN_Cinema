param(
    [Parameter(Mandatory = $true)]
    [string]$ImageRepo,
    [Parameter(Mandatory = $true)]
    [string]$Tag
)

$ErrorActionPreference = "Stop"

function Build-And-Push {
    param(
        [string]$ServiceName,
        [string]$DockerfilePath
    )

    if (-not (Test-Path $DockerfilePath)) {
        Write-Warning "Skip ${ServiceName}: Dockerfile not found at $DockerfilePath"
        return
    }

    $image = "$ImageRepo/$ServiceName`:$Tag"
    Write-Host ">>> Building $image"
    docker build -t $image -f $DockerfilePath .
    Write-Host ">>> Pushing $image"
    docker push $image
}

Build-And-Push -ServiceName "identity-service" -DockerfilePath "./identity-service/Dockerfile"
Build-And-Push -ServiceName "user-service" -DockerfilePath "./user-service/Dockerfile"
Build-And-Push -ServiceName "film-service" -DockerfilePath "./film-service/Dockerfile"
Build-And-Push -ServiceName "showtime-service" -DockerfilePath "./showtime-service/Dockerfile"
Build-And-Push -ServiceName "hall-services" -DockerfilePath "./hall-service/Dockerfile"
Build-And-Push -ServiceName "cinema-service" -DockerfilePath "./cinema-service/Dockerfile"
Build-And-Push -ServiceName "seat-service" -DockerfilePath "./seat-service/Dockerfile"
Build-And-Push -ServiceName "email-service" -DockerfilePath "./email-service/Dockerfile"
Build-And-Push -ServiceName "booking-service" -DockerfilePath "./booking-service/Dockerfile"
Build-And-Push -ServiceName "payment-service" -DockerfilePath "./payment-service/Dockerfile"

Write-Host "Done."
Write-Host "Set these vars on server before deploy:"
Write-Host "  export IMAGE_REPO=$ImageRepo"
Write-Host "  export IMAGE_TAG=$Tag"
