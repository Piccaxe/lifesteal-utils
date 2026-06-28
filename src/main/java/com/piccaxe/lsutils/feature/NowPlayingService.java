package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Reads "now playing" info and controls playback through the Windows System Media Transport
 * Controls (SMTC) — the same media data the OS shows in the volume flyout, so it works with
 * Spotify (free or premium) and any other player. There is no clean pure-Java path to WinRT, so
 * this shells out to a small PowerShell helper (written to the config dir on first use).
 *
 * <p>A daemon thread polls the current track every few seconds while the overlay is enabled;
 * control actions (next/prev/play-pause) run on a separate single-thread executor so they never
 * block the client thread. Windows-only — a no-op on other platforms.
 */
public final class NowPlayingService {
	private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

	private static volatile String title = "";
	private static volatile String artist = "";
	private static volatile boolean playing = false;
	private static volatile boolean available = false;
	private static volatile double posSeconds = 0.0;
	private static volatile double durSeconds = 0.0;
	private static volatile long pollTimeMs = 0L;
	private static volatile long refreshAt = 0L;

	private static Path scriptPath = null;
	private static Path artPath = null;
	private static ExecutorService exec;

	private NowPlayingService() {
	}

	public static void register() {
		if (!WINDOWS) {
			return;
		}
		exec = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "lsutils-nowplaying-ctl");
			t.setDaemon(true);
			return t;
		});
		Thread poller = new Thread(NowPlayingService::pollLoop, "lsutils-nowplaying-poll");
		poller.setDaemon(true);
		poller.start();
	}

	public static boolean supported() {
		return WINDOWS;
	}

	public static String getTitle() {
		return title;
	}

	public static String getArtist() {
		return artist;
	}

	public static boolean isPlaying() {
		return playing;
	}

	public static boolean isAvailable() {
		return available;
	}

	public static double getDurationSeconds() {
		return durSeconds;
	}

	/** Extrapolated playback position: the last polled position plus elapsed wall-clock if playing. */
	public static double getElapsedSeconds() {
		double base = posSeconds;
		if (playing) {
			base += (System.currentTimeMillis() - pollTimeMs) / 1000.0;
		}
		if (durSeconds > 0) {
			base = Math.min(base, durSeconds);
		}
		return Math.max(0.0, base);
	}

	public static boolean hasArt() {
		return artPath != null && Files.exists(artPath);
	}

	public static Path getArtPath() {
		return artPath;
	}

	public static void next() {
		control("next");
	}

	public static void previous() {
		control("prev");
	}

	public static void playPause() {
		control("playpause");
	}

	private static void control(String action) {
		if (!WINDOWS || exec == null) {
			return;
		}
		exec.submit(() -> {
			try {
				runScript(action);
			} catch (Exception ignored) {
			}
		});
		refreshAt = System.currentTimeMillis() + 400L; // poll again shortly so the UI catches up
	}

	private static void pollLoop() {
		while (true) {
			try {
				boolean on = ConfigManager.get() != null && ConfigManager.get().musicOverlay;
				if (!on) {
					available = false;
					Thread.sleep(1000L);
					continue;
				}
				poll();
				long now = System.currentTimeMillis();
				long sleep = refreshAt > now ? Math.max(250L, refreshAt - now) : 1000L;
				Thread.sleep(sleep);
			} catch (InterruptedException e) {
				return;
			} catch (Exception e) {
				available = false;
				try {
					Thread.sleep(2500L);
				} catch (InterruptedException ie) {
					return;
				}
			}
		}
	}

	private static void poll() throws Exception {
		String out = runScript("nowplaying");
		String line = null;
		for (String l : out.split("\\R")) {
			String t = l.trim();
			if (!t.isEmpty()) {
				line = t;
				break;
			}
		}
		if (line == null || line.equals("NONE")) {
			available = false;
			return;
		}
		String[] parts = line.split("\t", -1);
		title = parts.length > 0 ? parts[0] : "";
		artist = parts.length > 1 ? parts[1] : "";
		playing = parts.length > 2 && parts[2].equalsIgnoreCase("Playing");
		posSeconds = parts.length > 3 ? parseD(parts[3]) : 0.0;
		durSeconds = parts.length > 4 ? parseD(parts[4]) : 0.0;
		pollTimeMs = System.currentTimeMillis();
		available = !title.isEmpty();
	}

	private static double parseD(String s) {
		try {
			return Double.parseDouble(s.trim());
		} catch (NumberFormatException e) {
			return 0.0;
		}
	}

	private static String runScript(String action) throws Exception {
		Path ps1 = ensureScript();
		ProcessBuilder pb = new ProcessBuilder(
			"powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", ps1.toString(), action);
		pb.redirectErrorStream(true);
		Process p = pb.start();
		byte[] data = p.getInputStream().readAllBytes(); // drains fully, so the buffer can't deadlock
		p.waitFor(8, TimeUnit.SECONDS);
		if (p.isAlive()) {
			p.destroyForcibly();
		}
		return new String(data, StandardCharsets.UTF_8);
	}

	private static synchronized Path ensureScript() throws Exception {
		if (scriptPath != null && Files.exists(scriptPath)) {
			return scriptPath;
		}
		Path dir = FabricLoader.getInstance().getConfigDir();
		Path p = dir.resolve("lsutils_nowplaying.ps1");
		Files.writeString(p, SCRIPT, StandardCharsets.UTF_8);
		scriptPath = p;
		artPath = dir.resolve("lsutils_albumart.png");
		return p;
	}

	// PowerShell helper: queries SMTC for the current session, then either prints "Title\tArtist\tStatus"
	// or performs a control action. Uses the WinRT AsTask bridge to await the IAsyncOperations.
	private static final String SCRIPT =
		"param([string]$action = \"nowplaying\")\n"
			+ "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8\n"
			+ "$inv = [System.Globalization.CultureInfo]::InvariantCulture\n"
			+ "try {\n"
			+ "  Add-Type -AssemblyName System.Runtime.WindowsRuntime\n"
			+ "  $asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]\n"
			+ "  function Await($op, $t) { $task = $asTask.MakeGenericMethod($t).Invoke($null, @($op)); $task.Wait(-1) | Out-Null; $task.Result }\n"
			+ "  [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime] | Out-Null\n"
			+ "  [Windows.Storage.Streams.IRandomAccessStreamWithContentType,Windows.Storage.Streams,ContentType=WindowsRuntime] | Out-Null\n"
			+ "  $mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])\n"
			+ "  $s = $mgr.GetCurrentSession()\n"
			+ "  if ($null -eq $s) { Write-Output 'NONE'; exit }\n"
			+ "  switch ($action) {\n"
			+ "    'next' { Await ($s.TrySkipNextAsync()) ([bool]) | Out-Null; exit }\n"
			+ "    'prev' { Await ($s.TrySkipPreviousAsync()) ([bool]) | Out-Null; exit }\n"
			+ "    'playpause' { Await ($s.TryTogglePlayPauseAsync()) ([bool]) | Out-Null; exit }\n"
			+ "  }\n"
			+ "  $props = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])\n"
			+ "  $tl = $s.GetTimelineProperties()\n"
			+ "  $status = $s.GetPlaybackInfo().PlaybackStatus\n"
			+ "  $artPng = Join-Path $PSScriptRoot 'lsutils_albumart.png'\n"
			+ "  $trackFile = Join-Path $PSScriptRoot 'lsutils_lasttrack.txt'\n"
			+ "  $key = \"$($props.Title)|$($props.Artist)\"\n"
			+ "  $prev = ''\n"
			+ "  if (Test-Path $trackFile) { $prev = (Get-Content $trackFile -Raw -ErrorAction SilentlyContinue) }\n"
			+ "  if ($key -ne $prev) {\n"
			+ "    Set-Content -Path $trackFile -Value $key -NoNewline\n"
			+ "    $thumb = $props.Thumbnail\n"
			+ "    if ($null -ne $thumb) {\n"
			+ "      try {\n"
			+ "        $stream = Await ($thumb.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])\n"
			+ "        $m = ([System.IO.WindowsRuntimeStreamExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsStreamForRead' -and $_.GetParameters().Count -eq 1 })[0]\n"
			+ "        $net = $m.Invoke($null, @($stream))\n"
			+ "        $ms = New-Object System.IO.MemoryStream\n"
			+ "        $net.CopyTo($ms)\n"
			+ "        $tmp = \"$artPng.tmp\"\n"
			+ "        [System.IO.File]::WriteAllBytes($tmp, $ms.ToArray())\n"
			+ "        [System.IO.File]::Copy($tmp, $artPng, $true)\n"
			+ "        Remove-Item $tmp -ErrorAction SilentlyContinue\n"
			+ "      } catch { }\n"
			+ "    } else {\n"
			+ "      Remove-Item $artPng -ErrorAction SilentlyContinue\n"
			+ "    }\n"
			+ "  }\n"
			+ "  $pos = $tl.Position.TotalSeconds.ToString($inv)\n"
			+ "  $end = $tl.EndTime.TotalSeconds.ToString($inv)\n"
			+ "  Write-Output (\"$($props.Title)`t$($props.Artist)`t$status`t$pos`t$end\")\n"
			+ "} catch { Write-Output 'NONE' }\n";
}
