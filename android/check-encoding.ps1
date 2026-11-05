# Check that Kotlin/XML sources are valid UTF-8 (mojibake gives U+FFFD replacement chars)
$files = Get-ChildItem -Recurse -File "app/src/main/java", "app/src/main/res", "app/src/main/AndroidManifest.xml" -ErrorAction SilentlyContinue
$bad = @()
foreach ($f in $files) {
  $text = Get-Content -Raw -Encoding UTF8 $f.FullName
  if ($text.Contains([char]0xFFFD)) { $bad += $f.FullName }
}
if ($bad.Count -eq 0) { "OK: all files valid UTF-8" } else { $bad }
