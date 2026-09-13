use std::sync::Mutex;
use tauri::{AppHandle, Manager};
use tauri_plugin_shell::process::CommandChild;
use tauri_plugin_shell::ShellExt;
use std::net::TcpListener;
use tauri_plugin_dialog::DialogExt;

/// Holds the spawned backend process handle so it persists for the app's lifetime
/// and can be explicitly killed during shutdown.
pub struct BackendChild(pub Mutex<Option<CommandChild>>);

/// Kills any orphaned Java processes left over from a previous crash.
///
/// This prevents "port 8080 already in use" failures when the frontend was
/// force-killed but the backend Java process survived. We specifically target
/// only processes running our MediaflowArchiver.jar to avoid killing unrelated
/// Java applications.
fn cleanup_orphaned_backend() {
    use std::process::Command;

    // Find java processes running our specific JAR
    let output = Command::new("sh")
        .args(["-c", "pgrep -f 'MediaflowArchiver.jar' 2>/dev/null"])
        .output();

    if let Ok(out) = output {
        let pids = String::from_utf8_lossy(&out.stdout);
        for pid_str in pids.lines() {
            if let Ok(pid) = pid_str.trim().parse::<i32>() {
                eprintln!("[Sidecar] Found orphaned backend process (PID {}), killing it.", pid);
                let _ = Command::new("kill").args(["-9", &pid.to_string()]).output();
            }
        }
        // Give the OS a moment to release the port
        if !pids.trim().is_empty() {
            std::thread::sleep(std::time::Duration::from_millis(1500));
        }
    }
}

/// Launches the Spring Boot backend JAR as a Tauri-managed sidecar process.
///
/// The child process handle is stored in Tauri's managed state to prevent
/// orphaned Java processes if the app crashes or is force-killed.
/// All stdout/stderr from the backend is logged to the Tauri console for debugging.
pub fn launch_backend(app: &AppHandle) -> Result<(), Box<dyn std::error::Error>> {
    // Step 1: Kill any orphaned backend processes from prior crashes
    cleanup_orphaned_backend();

    // Step 2: Check if port 8080 is available (after orphan cleanup)
    if TcpListener::bind(("127.0.0.1", 8080)).is_err() {
        eprintln!("[Sidecar ERROR] Port 8080 is still in use after orphan cleanup.");
        let msg = "Port 8080 is currently in use by another application. MediaFlow Archiver requires this port to run its AI engine.\n\nPlease close the conflicting application and restart MediaFlow.";
        let _ = app.dialog().message(msg).title("Port Conflict").kind(tauri_plugin_dialog::MessageDialogKind::Error).blocking_show();
        std::process::exit(1);
    }

    let resource_dir = app.path().resource_dir().expect("Failed to get resource dir");
    let jar_path = resource_dir.join("resources").join("MediaflowArchiver.jar");

    if !jar_path.exists() {
        eprintln!("[Sidecar ERROR] Backend JAR not found at: {:?}", jar_path);
        return Err("Backend JAR not found".into());
    }

    let jar_str = jar_path
        .to_str()
        .ok_or("JAR path contains non-UTF8 characters")?;

    // Retrieve GEMINI_API_KEY from bash environment if missing (e.g. when launched via Rofi)
    let mut api_key = std::env::var("GEMINI_API_KEY").unwrap_or_default();
    if api_key.is_empty() {
        if let Ok(output) = std::process::Command::new("bash")
            .args(["-i", "-c", "echo $GEMINI_API_KEY"])
            .output() {
            api_key = String::from_utf8_lossy(&output.stdout).trim().to_string();
        }
    }

    let command = app
        .shell()
        .command("java")
        .env("GEMINI_API_KEY", api_key)
        .args([
            "-Xmx512m",
            "-jar",
            jar_str,
            "--spring.profiles.active=desktop",
            "--server.address=127.0.0.1",
            "--server.port=8080",
        ]);

    let (mut rx, child) = command.spawn().map_err(|e| {
        eprintln!("[Sidecar] Failed to spawn backend: {}", e);
        e
    })?;

    // Store the child handle in Tauri's managed state to prevent orphan processes
    app.manage(BackendChild(Mutex::new(Some(child))));

    // Stream sidecar output to Tauri console (visible in `tauri dev` terminal)
    tauri::async_runtime::spawn(async move {
        use tauri_plugin_shell::process::CommandEvent;
        while let Some(event) = rx.recv().await {
            match event {
                CommandEvent::Stdout(line) => {
                    let text = String::from_utf8_lossy(&line);
                    // Filter noisy Spring Boot startup lines in release mode
                    if !text.trim().is_empty() {
                        println!("[Backend] {}", text.trim_end());
                    }
                }
                CommandEvent::Stderr(line) => {
                    let text = String::from_utf8_lossy(&line);
                    if !text.trim().is_empty() {
                        eprintln!("[Backend] {}", text.trim_end());
                    }
                }
                CommandEvent::Terminated(status) => {
                    eprintln!(
                        "[Backend] Spring Boot process exited with status: {:?}",
                        status
                    );
                    break;
                }
                _ => {}
            }
        }
    });

    println!("[Sidecar] Spring Boot backend launched successfully.");
    Ok(())
}
