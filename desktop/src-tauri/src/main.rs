#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]
use std::sync::Arc;
use tauri::{Manager, State};
use thumbpad_core::{Receiver, Snapshot};
#[tauri::command]
fn snapshot(r: State<Arc<Receiver>>) -> Snapshot {
    r.snapshot()
}
#[tauri::command]
fn approve(r: State<Arc<Receiver>>, id: String, allow: bool) -> Result<(), String> {
    r.approve(&id, allow).map_err(|e| e.to_string())
}
#[tauri::command]
fn revoke(r: State<Arc<Receiver>>, id: String) -> Result<(), String> {
    r.revoke(&id).map_err(|e| e.to_string())
}
#[tauri::command]
fn enabled(r: State<Arc<Receiver>>, value: bool) {
    r.set_enabled(value)
}
#[tauri::command]
fn disconnect(r: State<Arc<Receiver>>) {
    r.disconnect()
}
#[tauri::command]
fn rotate(r: State<Arc<Receiver>>) {
    r.rotate()
}
#[tauri::command]
fn qr(r: State<Arc<Receiver>>) -> Result<String, String> {
    qrcode::QrCode::new(r.snapshot().pairing_uri)
        .map(|q| {
            q.render::<qrcode::render::svg::Color>()
                .min_dimensions(240, 240)
                .build()
        })
        .map_err(|e| e.to_string())
}
#[tauri::command]
fn permissions() -> Result<(), String> {
    #[cfg(target_os = "macos")]
    {
        open::that("x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility")
            .map_err(|e| e.to_string())?;
    }
    Ok(())
}
fn main() {
    tauri::Builder::default()
        .plugin(tauri_plugin_autostart::Builder::new().build())
        .setup(|app| {
            let r = Receiver::new(false, false, None, thumbpad_core::PORT)?;
            app.manage(r.clone());
            tauri::async_runtime::spawn(async move {
                if let Err(e) = r.clone().serve().await {
                    eprintln!("Receiver failed: {e}");
                    r.set_enabled(false);
                }
            });
            let show =
                tauri::menu::MenuItem::with_id(app, "show", "Open ThumbPad", true, None::<&str>)?;
            let stop = tauri::menu::MenuItem::with_id(
                app,
                "stop",
                "Disconnect / disable",
                true,
                None::<&str>,
            )?;
            let quit = tauri::menu::MenuItem::with_id(app, "quit", "Quit", true, None::<&str>)?;
            let menu = tauri::menu::Menu::with_items(app, &[&show, &stop, &quit])?;
            tauri::tray::TrayIconBuilder::new()
                .icon(app.default_window_icon().unwrap().clone())
                .tooltip("ThumbPad")
                .menu(&menu)
                .on_menu_event(|app, e| match e.id.as_ref() {
                    "show" => {
                        if let Some(w) = app.get_webview_window("main") {
                            let _ = w.show();
                            let _ = w.set_focus();
                        }
                    }
                    "stop" => app.state::<Arc<Receiver>>().set_enabled(false),
                    "quit" => {
                        app.state::<Arc<Receiver>>().set_enabled(false);
                        let handle = app.clone();
                        tauri::async_runtime::spawn(async move {
                            std::thread::sleep(std::time::Duration::from_millis(150));
                            handle.exit(0);
                        });
                    }
                    _ => (),
                })
                .build(app)?;
            Ok(())
        })
        .on_window_event(|w, e| {
            if let tauri::WindowEvent::CloseRequested { api, .. } = e {
                api.prevent_close();
                let _ = w.hide();
            }
        })
        .invoke_handler(tauri::generate_handler![
            snapshot,
            approve,
            revoke,
            enabled,
            disconnect,
            rotate,
            qr,
            permissions
        ])
        .run(tauri::generate_context!())
        .expect("ThumbPad could not start; check secure storage and LAN availability");
}
