use tauri::Manager;
use tauri_plugin_dialog::DialogExt;

mod sidecar;
mod tray;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_notification::init())
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_single_instance::init(|app, _args, _cwd| {
            if let Some(window) = app.get_webview_window("main") {
                let _ = window.show();
                let _ = window.unminimize();
                let _ = window.set_focus();
            }
        }))
        .setup(|app| {
            // Launch the Spring Boot backend as a managed sidecar process
            sidecar::launch_backend(app.handle())?;

            // Set up the system tray icon and menu
            tray::setup_tray(app)?;

            // Start backend health polling to coordinate windows
            let app_handle = app.handle().clone();
            tauri::async_runtime::spawn(async move {
                let client = reqwest::Client::builder()
                    .timeout(std::time::Duration::from_secs(2))
                    .build()
                    .unwrap();
                let mut attempts = 0;
                loop {
                    match client.get("http://127.0.0.1:8080/actuator/health").send().await {
                        Ok(res) if res.status().is_success() => {
                            println!("[HealthCheck] Spring Boot backend is UP!");
                            // Close splash and show main
                            if let Some(splash_window) = app_handle.get_webview_window("splashscreen") {
                                let _ = splash_window.close();
                            }
                            if let Some(main_window) = app_handle.get_webview_window("main") {
                                let _ = main_window.show();
                                let _ = main_window.set_focus();
                            }
                            break;
                        }
                        _ => {
                            attempts += 1;
                            if attempts > 120 { // 60 seconds timeout
                                eprintln!("[HealthCheck] Backend failed to start after 60 seconds.");
                                let msg = "The MediaFlow AI Engine failed to start within the expected time. Please check the logs or ensure PostgreSQL is running.";
                                let _ = app_handle.dialog().message(msg).title("Boot Failure").kind(tauri_plugin_dialog::MessageDialogKind::Error).blocking_show();
                                std::process::exit(1);
                            }
                            // Sleep for 500ms before retrying
                            tokio::time::sleep(std::time::Duration::from_millis(500)).await;
                        }
                    }
                }
            });

            Ok(())
        })
        // Explicitly kill the backend sidecar when the MAIN window is destroyed
        .on_window_event(|window, event| {
            if let tauri::WindowEvent::Destroyed = event {
                if window.label() == "main" {
                    if let Some(state) = window.try_state::<sidecar::BackendChild>() {
                        if let Some(child) = state.0.lock().unwrap().take() {
                            eprintln!("[Sidecar] Killing backend process on main window close.");
                            let _ = child.kill();
                        }
                    }
                }
            }
        })
        .run(tauri::generate_context!())
        .expect("Error running MediaFlow Archiver");
}
