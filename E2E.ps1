# test-altrix-jakarta-pubsub — build + run + full E2E smoke test (PowerShell)
# Run section by section, or the whole file top to bottom.

# ── 0) Prerequisite: Pub/Sub emulator must be running ──────────────────────
# (part of backend/docker-compose.yml — altrix-pubsub-emulator, host port 8085)
docker ps --filter "name=altrix-pubsub-emulator" --format "{{.Names}}: {{.Status}}"

# ── 1) Provision topics + subscriptions in the emulator (idempotent — safe to ──
#       re-run; the emulator only holds these in memory, so re-create after any
#       emulator container restart)
$base = "http://localhost:8085/v1/projects/altrix-local"
$topics = @("orders.created", "payments.completed", "payments.refunded")
foreach ($t in $topics) {
    try { Invoke-RestMethod -Method Put -Uri "$base/topics/$t" -ErrorAction Stop | Out-Null; Write-Host "topic created: $t" }
    catch { Write-Host "topic $t already exists (ok)" }
}
$subs = @{
    "orders.created.processor"         = "orders.created"
    "orders.created.payments"          = "orders.created"
    "payments.completed.orders"        = "payments.completed"
    "payments.completed.notifications" = "payments.completed"
    "payments.refunded.notifications"  = "payments.refunded"
}
foreach ($s in $subs.Keys) {
    $body = @{ topic = "projects/altrix-local/topics/$($subs[$s])" } | ConvertTo-Json
    try { Invoke-RestMethod -Method Put -Uri "$base/subscriptions/$s" -Body $body -ContentType "application/json" -ErrorAction Stop | Out-Null; Write-Host "sub created: $s" }
    catch { Write-Host "sub $s already exists (ok)" }
}

# ── 2) Kill any stale instance FIRST — Windows locks the jar file while a ──────
#       previous run is still alive, which makes the next `clean` fail with
#       "Failed to delete ...microbundle.jar". Always run this before building.
Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
    Where-Object { $_.CommandLine -like "*orders-jakarta-pubsub-microbundle*" } |
    ForEach-Object { Write-Host "killing stale PID $($_.ProcessId)"; Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
Start-Sleep -Seconds 1

# ── 3) Build (mvn isn't installed locally — build via the same Docker image ────
#       the Altrix sandbox uses). -Xmx2g avoids an OOM in the Payara Micro
#       bundling step if other heavy containers (SonarQube/DefectDojo) are
#       also running.
docker run --rm -e MAVEN_OPTS="-Xmx2g" `
    -v "C:/Users/SALAH/Projects/test-altrix-jakarta-pubsub:/workspace" `
    -v "C:/Users/SALAH/.m2:/root/.m2" `
    -w /workspace maven:3.9-eclipse-temurin-21-alpine `
    mvn -B clean package -DskipTests

# ── 4) Run
$env:PUBSUB_EMULATOR_HOST = "localhost:8085"
$env:GCP_PROJECT_ID = "altrix-local"
# -Xmx512m keeps this fixture's own footprint small — helpful if the host is
# tight on RAM (Docker Desktop + the rest of the Altrix stack already use a lot).
$p = Start-Process -FilePath "java" `
    -ArgumentList "-Xmx512m", "-jar", "target/orders-jakarta-pubsub-microbundle.jar", "--port", "8081" `
    -WorkingDirectory "C:\Users\SALAH\Projects\test-altrix-jakarta-pubsub" `
    -RedirectStandardOutput "$env:TEMP\pubsub-app-out.log" `
    -RedirectStandardError  "$env:TEMP\pubsub-app-err.log" `
    -PassThru
Write-Host "Started PID=$($p.Id)"

# Poll until Payara reports ready (or dies)
for ($i = 0; $i -lt 45; $i++) {
    Start-Sleep -Seconds 2
    if (-not (Get-Process -Id $p.Id -ErrorAction SilentlyContinue)) { Write-Host "Process died at t=$($i*2)s — check $env:TEMP\pubsub-app-err.log"; break }
    if ((Get-Content "$env:TEMP\pubsub-app-err.log" -Raw -ErrorAction SilentlyContinue) -match "ready in") { Write-Host "Ready at t=$($i*2)s"; break }
}
Get-Content "$env:TEMP\pubsub-app-err.log" | Select-String -Pattern "Subscribed to|unavailable"

# ── 5) E2E smoke test ────────────────────────────────────────────────────────
Write-Host "`n=== health ==="
(Invoke-WebRequest -Uri "http://localhost:8081/api/health" -UseBasicParsing).Content

Write-Host "`n=== place order ==="
$order = @{ id = "smoke-$(Get-Random)"; product = "Widget"; quantity = 3 } | ConvertTo-Json
$orderId = ($order | ConvertFrom-Json).id
$r1 = Invoke-WebRequest -Uri "http://localhost:8081/api/orders" -Method Post -Body $order -ContentType "application/json" -UseBasicParsing
Write-Host "$($r1.StatusCode) $($r1.Content)"
Start-Sleep -Seconds 2

Write-Host "`n=== order status (expect PAID) ==="
$r2 = Invoke-WebRequest -Uri "http://localhost:8081/api/orders/$orderId/status" -UseBasicParsing
Write-Host "$($r2.StatusCode) $($r2.Content)"

Write-Host "`n=== payment (expect COMPLETED) ==="
$r3 = Invoke-WebRequest -Uri "http://localhost:8081/api/payments/$orderId" -UseBasicParsing
Write-Host "$($r3.StatusCode) $($r3.Content)"

Write-Host "`n=== refund ==="
$r4 = Invoke-WebRequest -Uri "http://localhost:8081/api/payments/$orderId/refund" -Method Post -UseBasicParsing
Write-Host "$($r4.StatusCode) $($r4.Content)"
Start-Sleep -Seconds 2

Write-Host "`n=== payment after refund (expect REFUNDED) ==="
$r5 = Invoke-WebRequest -Uri "http://localhost:8081/api/payments/$orderId" -UseBasicParsing
Write-Host "$($r5.StatusCode) $($r5.Content)"

Write-Host "`n=== notification fan-out (from logs) ==="
Get-Content "$env:TEMP\pubsub-app-err.log" | Select-String -Pattern "Notifying customer"

# ── 6) Stop ──────────────────────────────────────────────────────────────────
# Stop-Process -Id $p.Id -Force
