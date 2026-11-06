# Mini driver: ui.ps1 cmd1 cmd2 ...   cmds: focus:<title> | focuspid:<pid> | place:pid,x,y,w,h | click:x,y | lclick | rclick | rdown | rup | mclick | ldown | lup | rel:dx,dy | key:VK[,ms] | down:VK | up:VK
#                                     | text:<s> | sleep:ms | shot:<file>   (coordinates in physical pixels)
Add-Type -Path "$PSScriptRoot\Input.cs"
Add-Type -AssemblyName System.Windows.Forms, System.Drawing
[UI]::SetProcessDPIAware() | Out-Null
foreach ($c in $args) {
  $k, $v = $c -split ':', 2
  switch ($k) {
    'focus' { if (-not [UI]::Focus($v)) { "no window $v" } }
    'focuspid' { if (-not [UI]::FocusPid([int]$v)) { "no window $v" } }
    'place' { $p = $v -split ','; [UI]::PlacePid([int]$p[0], [int]$p[1], [int]$p[2], [int]$p[3], [int]$p[4]) | Out-Null }
    'click' { $x, $y = $v -split ','; [UI]::Click([int]$x, [int]$y) }
    'move' { $x, $y = $v -split ','; [UI]::Move([int]$x, [int]$y) }
    'lclick' { [UI]::LClick() }
    'ldown' { [UI]::LDown() }
    'lup' { [UI]::LUp() }
    'rclick' { [UI]::RClick() }
    'rdown' { [UI]::RDown() }
    'rup' { [UI]::RUp() }
    'mclick' { [UI]::MClick() }
    'xdown' { [UI]::XDown([uint32]$v) }
    'xup' { [UI]::XUp([uint32]$v) }
    'rel' { $x, $y = $v -split ','; [UI]::Rel([int]$x, [int]$y) }
    'key' { $p = $v -split ','; $ms = 60; if ($p.Count -gt 1) { $ms = [int]$p[1] }; [UI]::Tap([uint16]$p[0], $ms) }
    'down' { [UI]::Key([uint16]$v, $false, $false) }
    'up' { [UI]::Key([uint16]$v, $true, $false) }
    'text' { [UI]::Text($v) }
    'sleep' { Start-Sleep -Milliseconds ([int]$v) }
    'shot' {
      $b = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds
      $bmp = New-Object System.Drawing.Bitmap $b.Width, $b.Height
      $g = [System.Drawing.Graphics]::FromImage($bmp)
      $g.CopyFromScreen($b.Location, [System.Drawing.Point]::Empty, $b.Size)
      $bmp.Save($v, [System.Drawing.Imaging.ImageFormat]::Png); $g.Dispose(); $bmp.Dispose()
    }
  }
}
