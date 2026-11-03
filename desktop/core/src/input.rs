use crate::protocol::Packet;
use anyhow::{bail, Result};

#[derive(Default)]
pub struct Input {
    held: [bool; 3],
    remainder: [f32; 2],
    dry: bool,
}
impl Input {
    pub fn new(dry: bool) -> Self {
        Self {
            dry,
            ..Self::default()
        }
    }
    pub fn dispatch(&mut self, p: Packet) -> Result<()> {
        match p.kind {
            1 => {
                self.remainder[0] += p.a;
                self.remainder[1] += p.b;
                let x = self.remainder[0].trunc() as i32;
                let y = self.remainder[1].trunc() as i32;
                self.remainder[0] -= x as f32;
                self.remainder[1] -= y as f32;
                if !self.dry {
                    native::motion(x, y)?;
                }
            }
            2 => {
                let i = p.a as usize;
                let down = p.b == 1.0;
                if self.held[i] != down {
                    if !self.dry {
                        native::button(i, down)?;
                    }
                    self.held[i] = down;
                }
            }
            3 => {
                if !self.dry {
                    native::scroll(p.a, p.b)?;
                }
            }
            4 => {
                if !self.dry {
                    native::shortcut(p.a as u8)?;
                }
            }
            6 => self.release(),
            _ => (),
        }
        Ok(())
    }
    pub fn release(&mut self) {
        for i in 0..3 {
            if self.held[i] {
                if !self.dry {
                    let _ = native::button(i, false);
                }
                self.held[i] = false;
            }
        }
        self.remainder = [0.0; 2];
    }
}
impl Drop for Input {
    fn drop(&mut self) {
        self.release();
    }
}

#[cfg(target_os = "windows")]
mod native {
    use super::*;
    #[repr(C)]
    #[derive(Clone, Copy)]
    struct Mouse {
        dx: i32,
        dy: i32,
        data: u32,
        flags: u32,
        time: u32,
        extra: usize,
    }
    #[repr(C)]
    #[derive(Clone, Copy)]
    struct Key {
        vk: u16,
        scan: u16,
        flags: u32,
        time: u32,
        extra: usize,
    }
    #[repr(C)]
    union Payload {
        mouse: Mouse,
        key: Key,
    }
    #[repr(C)]
    struct Event {
        kind: u32,
        payload: Payload,
    }
    #[link(name = "user32")]
    extern "system" {
        fn SendInput(count: u32, input: *const Event, size: i32) -> u32;
    }
    fn mouse(dx: i32, dy: i32, data: u32, flags: u32) -> Result<()> {
        let e = Event {
            kind: 0,
            payload: Payload {
                mouse: Mouse {
                    dx,
                    dy,
                    data,
                    flags,
                    time: 0,
                    extra: 0,
                },
            },
        };
        if unsafe { SendInput(1, &e, std::mem::size_of::<Event>() as i32) } != 1 {
            bail!("Windows rejected input (locked desktop or elevated app)");
        }
        Ok(())
    }
    pub fn motion(x: i32, y: i32) -> Result<()> {
        mouse(x, y, 0, 0x0001)
    }
    pub fn button(i: usize, down: bool) -> Result<()> {
        mouse(0, 0, 0, [[2, 4], [8, 16], [32, 64]][i][usize::from(!down)])
    }
    pub fn scroll(x: f32, y: f32) -> Result<()> {
        if y != 0.0 {
            mouse(0, 0, (-y * 3.0).round() as i32 as u32, 0x0800)?;
        }
        if x != 0.0 {
            mouse(0, 0, (x * 3.0).round() as i32 as u32, 0x1000)?;
        }
        Ok(())
    }
    pub fn shortcut(id: u8) -> Result<()> {
        let vk = [0xA6, 0xA7, 0xB3, 0xAF, 0xAE, 0x0D, 0x1B, 0x09, 0x08][id as usize];
        for flags in [0, 2] {
            let e = Event {
                kind: 1,
                payload: Payload {
                    key: Key {
                        vk,
                        scan: 0,
                        flags,
                        time: 0,
                        extra: 0,
                    },
                },
            };
            if unsafe { SendInput(1, &e, std::mem::size_of::<Event>() as i32) } != 1 {
                bail!("keyboard injection rejected");
            }
        }
        Ok(())
    }
}
#[cfg(target_os = "macos")]
mod native {
    use super::*;
    use std::ffi::c_void;
    #[repr(C)]
    #[derive(Clone, Copy)]
    struct Point {
        x: f64,
        y: f64,
    }
    #[link(name = "ApplicationServices", kind = "framework")]
    extern "C" {
        fn CGEventCreate(source: *const c_void) -> *mut c_void;
        fn CGEventGetLocation(event: *const c_void) -> Point;
        fn CGEventCreateMouseEvent(
            source: *const c_void,
            kind: u32,
            point: Point,
            button: u32,
        ) -> *mut c_void;
        fn CGEventCreateScrollWheelEvent(
            source: *const c_void,
            units: u32,
            count: u32,
            ...
        ) -> *mut c_void;
        fn CGEventCreateKeyboardEvent(source: *const c_void, key: u16, down: bool) -> *mut c_void;
        fn CGEventSetFlags(event: *mut c_void, flags: u64);
        fn CGEventPost(tap: u32, event: *const c_void);
        fn CGEventSourceButtonState(state: i32, button: u32) -> bool;
        pub fn AXIsProcessTrusted() -> bool;
    }
    #[link(name = "CoreFoundation", kind = "framework")]
    extern "C" {
        fn CFRelease(p: *const c_void);
    }
    #[link(name = "AppKit", kind = "framework")]
    extern "C" {}
    #[link(name = "objc")]
    extern "C" {
        fn objc_getClass(name: *const std::ffi::c_char) -> *mut c_void;
        fn sel_registerName(name: *const std::ffi::c_char) -> *const c_void;
        fn objc_msgSend();
    }
    // NSEvent system-defined media events carry NX_KEYTYPE values. Quartz keyboard
    // virtual codes alone do not represent play/pause or volume on macOS.
    unsafe fn media(key: i64) -> Result<()> {
        let send0: unsafe extern "C" fn(*mut c_void, *const c_void) -> *mut c_void =
            std::mem::transmute(objc_msgSend as *const ());
        let send: unsafe extern "C" fn(
            *mut c_void,
            *const c_void,
            u64,
            Point,
            u64,
            f64,
            i64,
            *const c_void,
            i16,
            i64,
            i64,
        ) -> *mut c_void = std::mem::transmute(objc_msgSend as *const ());
        let pool = send0(
            objc_getClass(c"NSAutoreleasePool".as_ptr()),
            sel_registerName(c"new".as_ptr()),
        );
        let result = (|| {
            for state in [0xA, 0xB] {
                let event=send(objc_getClass(c"NSEvent".as_ptr()),sel_registerName(c"otherEventWithType:location:modifierFlags:timestamp:windowNumber:context:subtype:data1:data2:".as_ptr()),14,Point{x:0.0,y:0.0},0,0.0,0,std::ptr::null(),8,(key<<16)|(state<<8),-1);
                if event.is_null() {
                    bail!("macOS media event unavailable");
                }
                let cg = send0(event, sel_registerName(c"CGEvent".as_ptr()));
                if cg.is_null() {
                    bail!("macOS media CGEvent unavailable");
                }
                CGEventPost(0, cg);
            }
            Ok(())
        })();
        send0(pool, sel_registerName(c"drain".as_ptr()));
        result
    }
    fn post(e: *mut c_void) -> Result<()> {
        if e.is_null() {
            bail!("CGEvent unavailable");
        }
        unsafe {
            CGEventPost(0, e);
            CFRelease(e);
        }
        Ok(())
    }
    fn point() -> Point {
        unsafe {
            let e = CGEventCreate(std::ptr::null());
            if e.is_null() {
                return Point { x: 0.0, y: 0.0 };
            }
            let p = CGEventGetLocation(e);
            CFRelease(e);
            p
        }
    }
    pub fn motion(x: i32, y: i32) -> Result<()> {
        let mut p = point();
        p.x += x as f64;
        p.y += y as f64;
        unsafe {
            let kind = if CGEventSourceButtonState(0, 0) {
                6
            } else if CGEventSourceButtonState(0, 1) {
                7
            } else if CGEventSourceButtonState(0, 2) {
                27
            } else {
                5
            };
            post(CGEventCreateMouseEvent(
                std::ptr::null(),
                kind,
                p,
                if kind == 7 {
                    1
                } else if kind == 27 {
                    2
                } else {
                    0
                },
            ))
        }
    }
    pub fn button(i: usize, down: bool) -> Result<()> {
        unsafe {
            post(CGEventCreateMouseEvent(
                std::ptr::null(),
                [[1, 2], [3, 4], [25, 26]][i][usize::from(!down)],
                point(),
                i as u32,
            ))
        }
    }
    pub fn scroll(x: f32, y: f32) -> Result<()> {
        unsafe {
            post(CGEventCreateScrollWheelEvent(
                std::ptr::null(),
                0,
                2,
                (-y).round() as i32,
                (-x).round() as i32,
            ))
        }
    }
    pub fn shortcut(id: u8) -> Result<()> {
        if (2..=4).contains(&id) {
            return unsafe { media([16, 0, 1][(id - 2) as usize]) };
        }
        let key = [33, 30, 0, 0, 0, 36, 53, 48, 51][id as usize];
        for down in [true, false] {
            unsafe {
                let event = CGEventCreateKeyboardEvent(std::ptr::null(), key, down);
                if event.is_null() {
                    bail!("macOS keyboard event unavailable");
                }
                if id <= 1 {
                    CGEventSetFlags(event, 1 << 20);
                }
                post(event)?;
            }
        }
        Ok(())
    }
}
#[cfg(not(any(target_os = "windows", target_os = "macos")))]
mod native {
    use super::*;
    pub fn motion(_: i32, _: i32) -> Result<()> {
        bail!("unsupported OS")
    }
    pub fn button(_: usize, _: bool) -> Result<()> {
        bail!("unsupported OS")
    }
    pub fn scroll(_: f32, _: f32) -> Result<()> {
        bail!("unsupported OS")
    }
    pub fn shortcut(_: u8) -> Result<()> {
        bail!("unsupported OS")
    }
}

pub fn accessibility() -> bool {
    #[cfg(target_os = "macos")]
    {
        unsafe { native::AXIsProcessTrusted() }
    }
    #[cfg(not(target_os = "macos"))]
    {
        true
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn disconnect_releases_all() {
        let mut i = Input::new(true);
        for a in 0..3 {
            i.dispatch(Packet {
                kind: 2,
                sequence: 1,
                micros: 0,
                a: a as f32,
                b: 1.0,
            })
            .unwrap();
        }
        i.release();
        assert_eq!(i.held, [false; 3]);
    }
    #[test]
    fn repeated_down_is_idempotent() {
        let mut i = Input::new(true);
        let p = Packet {
            kind: 2,
            sequence: 1,
            micros: 0,
            a: 0.0,
            b: 1.0,
        };
        i.dispatch(p).unwrap();
        i.dispatch(p).unwrap();
        assert!(i.held[0]);
        i.release();
    }
}
