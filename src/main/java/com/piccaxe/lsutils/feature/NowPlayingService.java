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
	private static volatile long refreshAt = 0L;

	private static Path scriptPath = null;
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
				long sleep = refreshAt > now ? Math.max(300L, refreshAt - now) : 3000L;
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
		String[] parts = line.split("\t", 3);
		title = parts.length > 0 ? parts[0] : "";
		artist = parts.length > 1 ? parts[1] : "";
		playing = parts.length > 2 && parts[2].equalsIgnoreCase("Playing");
		available = !title.isEmpty();
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
		return p;
	}

	// PowerShell helper: queries SMTC for the current session, then either prints "Title\tArtist\tStatus"
	// or performs a control action. Uses the WinRT AsTask bridge to await the IAsyncOperations.
	private static final String SCRIPT =
		"param([string]$action = \"nowplaying\")\n"
			+ "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8\n"
			+ "try {\n"
			+ "  Add-Type -AssemblyName System.Runtime.WindowsRuntime\n"
			+ "  $asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]\n"
			+ "  function Await($op, $t) { $task = $asTask.MakeGenericMethod($t).Invoke($null, @($op)); $task.Wait(-1) | Out-Null; $task.Result }\n"
			+ "  [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime] | Out-Null\n"
			+ "  $mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])\n"
			+ "  $s = $mgr.GetCurrentSession()\n"
			+ "  if ($null -eq $s) { Write-Output 'NONE'; exit }\n"
			+ "  switch ($action) {\n"
			+ "    'next' { Await ($s.TrySkipNextAsync()) ([bool]) | Out-Null }\n"
			+ "    'prev' { Await ($s.TrySkipPreviousAsync()) ([bool]) | Out-Null }\n"
			+ "    'playpause' { Await ($s.TryTogglePlayPauseAsync()) ([bool]) | Out-Null }\n"
			+ "    default {\n"
			+ "      $props = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])\n"
			+ "      $status = $s.GetPlaybackInfo().PlaybackStatus\n"
			+ "      Write-Output (\"$($props.Title)`t$($props.Artist)`t$status\")\n"
			+ "    }\n"
			+ "  }\n"
			+ "} catch { Write-Output 'NONE' }\n";
}
