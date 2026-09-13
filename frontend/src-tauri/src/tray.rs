use tauri::{
    menu::{Menu, MenuItem},
    tray::TrayIconBuilder,
    App, Manager,
};

/// Sets up the system tray icon with Show/Quit actions.
///
/// When the user clicks "Show", the main window is brought to the front.
/// When the user clicks "Quit", the entire application (including the backend sidecar) exits.
pub fn setup_tray(app: &App) -> Result<(), Box<dyn std::error::Error>> {
    let show = MenuItem::with_id(app, "show", "Show MediaFlow", true, None::<&str>)?;
    let separator = MenuItem::with_id(app, "sep", "────────────", false, None::<&str>)?;
    let quit = MenuItem::with_id(app, "quit", "Quit", true, None::<&str>)?;

    let menu = Menu::with_items(app, &[&show, &separator, &quit])?;

    TrayIconBuilder::new()
        .icon(app.default_window_icon().unwrap().clone())
        .tooltip("MediaFlow Archiver")
        .menu(&menu)
        .show_menu_on_left_click(false)
        .on_menu_event(|app, event| match event.id.as_ref() {
            "show" => {
                if let Some(window) = app.get_webview_window("main") {
                    let _ = window.show();
                    let _ = window.unminimize();
                    let _ = window.set_focus();
                }
            }
            "quit" => {
                app.exit(0);
            }
            _ => {}
        })
        .build(app)?;

    Ok(())
}
