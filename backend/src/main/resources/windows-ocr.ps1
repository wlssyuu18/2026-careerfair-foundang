$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$null = [Windows.Storage.Streams.InMemoryRandomAccessStream, Windows.Storage.Streams, ContentType=WindowsRuntime]
$null = [Windows.Graphics.Imaging.BitmapDecoder, Windows.Graphics.Imaging, ContentType=WindowsRuntime]
$null = [Windows.Media.Ocr.OcrEngine, Windows.Foundation, ContentType=WindowsRuntime]
$null = [Windows.Globalization.Language, Windows.Globalization, ContentType=WindowsRuntime]
$taskAsTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.IsGenericMethod -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' } | Select-Object -First 1
function Await-Ocr($operation, [Type]$resultType) {
    $task = $taskAsTask.MakeGenericMethod($resultType).Invoke($null, @($operation))
    $task.GetAwaiter().GetResult()
}
$taskStream = [Windows.Storage.Streams.InMemoryRandomAccessStream]::new()
try {
    $taskWriter = [Windows.Storage.Streams.DataWriter]::new($taskStream)
    $taskWriter.WriteBytes([Convert]::FromBase64String([Console]::In.ReadToEnd()))
    $null = Await-Ocr ($taskWriter.StoreAsync()) ([uint32])
    $taskWriter.DetachStream() | Out-Null
    $taskWriter.Dispose()
    $taskStream.Seek(0)
    $taskDecoder = Await-Ocr ([Windows.Graphics.Imaging.BitmapDecoder]::CreateAsync($taskStream)) ([Windows.Graphics.Imaging.BitmapDecoder])
    $taskBitmap = Await-Ocr ($taskDecoder.GetSoftwareBitmapAsync()) ([Windows.Graphics.Imaging.SoftwareBitmap])
    try {
        $taskEngine = [Windows.Media.Ocr.OcrEngine]::TryCreateFromLanguage([Windows.Globalization.Language]::new('ko'))
        if ($null -eq $taskEngine) { exit 1 }
        $taskResult = Await-Ocr ($taskEngine.RecognizeAsync($taskBitmap)) ([Windows.Media.Ocr.OcrResult])
        [Console]::Write(($taskResult.Lines | ForEach-Object Text) -join "`n")
    } finally { $taskBitmap.Dispose() }
} finally { $taskStream.Dispose() }
