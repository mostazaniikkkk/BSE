using System; using System.Runtime.InteropServices; using System.Threading; using System.Diagnostics;
public static class UI {
  [StructLayout(LayoutKind.Sequential)] struct MOUSEINPUT { public int dx, dy; public uint data, flags, time; public IntPtr extra; }
  [StructLayout(LayoutKind.Sequential)] struct KEYBDINPUT { public ushort vk, scan; public uint flags, time; public IntPtr extra; }
  [StructLayout(LayoutKind.Explicit)] struct INPUT { [FieldOffset(0)] public uint type; [FieldOffset(8)] public MOUSEINPUT mi; [FieldOffset(8)] public KEYBDINPUT ki; }
  [DllImport("user32.dll")] static extern uint SendInput(uint n, INPUT[] i, int size);
  [DllImport("user32.dll")] static extern bool SetCursorPos(int x, int y);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int c);
  [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
  [DllImport("user32.dll")] static extern bool IsIconic(IntPtr h);
  [DllImport("user32.dll")] static extern uint MapVirtualKey(uint code, uint type);
  static void Send(INPUT i) { SendInput(1, new INPUT[] { i }, Marshal.SizeOf(typeof(INPUT))); }
  static void Mouse(uint flags, int dx, int dy, uint data) { INPUT i = new INPUT(); i.type = 0; i.mi.dx = dx; i.mi.dy = dy; i.mi.flags = flags; i.mi.data = data; Send(i); }
  public static void Move(int x, int y) { SetCursorPos(x, y); }
  public static void Rel(int dx, int dy) { Mouse(1, dx, dy, 0); }
  public static void Click(int x, int y) { SetCursorPos(x, y); Thread.Sleep(80); Mouse(2, 0, 0, 0); Thread.Sleep(60); Mouse(4, 0, 0, 0); }
  public static void LDown() { Mouse(2, 0, 0, 0); } public static void LUp() { Mouse(4, 0, 0, 0); }
  public static void XDown(uint b) { Mouse(0x80, 0, 0, b); } public static void XUp(uint b) { Mouse(0x100, 0, 0, b); }
  public static void RDown() { Mouse(8, 0, 0, 0); } public static void RUp() { Mouse(16, 0, 0, 0); }
  public static void RClick() { Mouse(8, 0, 0, 0); Thread.Sleep(60); Mouse(16, 0, 0, 0); }
  public static void MClick() { Mouse(0x20, 0, 0, 0); Thread.Sleep(60); Mouse(0x40, 0, 0, 0); }
  public static void LClick() { Mouse(2, 0, 0, 0); Thread.Sleep(60); Mouse(4, 0, 0, 0); }
  public static void Key(ushort vk, bool up, bool ext) { INPUT i = new INPUT(); i.type = 1; i.ki.scan = (ushort)MapVirtualKey(vk, 0); i.ki.vk = vk; i.ki.flags = 8u | (up ? 2u : 0u) | (ext ? 1u : 0u); Send(i); }
  public static void Tap(ushort vk, int ms) { Key(vk, false, false); Thread.Sleep(ms); Key(vk, true, false); }
  public static void Text(string s) { foreach (char c in s) { INPUT i = new INPUT(); i.type = 1; i.ki.scan = c; i.ki.flags = 4; Send(i); i.ki.flags = 6; Send(i); Thread.Sleep(15); } }
  [DllImport("user32.dll")] static extern bool MoveWindow(IntPtr h, int x, int y, int w, int hh, bool repaint);
  public static bool PlacePid(int pid, int x, int y, int w, int h) { var p = Process.GetProcessById(pid); return p.MainWindowHandle != IntPtr.Zero && MoveWindow(p.MainWindowHandle, x, y, w, h, true); }
  public static bool FocusPid(int pid) { var p = Process.GetProcessById(pid); if (p.MainWindowHandle == IntPtr.Zero) return false; if (IsIconic(p.MainWindowHandle)) ShowWindow(p.MainWindowHandle, 9); SetForegroundWindow(p.MainWindowHandle); return true; }
  public static bool Focus(string titlePart) { foreach (var p in Process.GetProcesses()) { if (p.MainWindowHandle != IntPtr.Zero && p.MainWindowTitle.Contains(titlePart)) { if (IsIconic(p.MainWindowHandle)) ShowWindow(p.MainWindowHandle, 9); SetForegroundWindow(p.MainWindowHandle); return true; } } return false; }
}
