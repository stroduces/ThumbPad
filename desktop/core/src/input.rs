use crate::protocol::Packet;
use anyhow::{bail, Result};

#[derive(Default)]
pub struct Input {
    held: [bool; 3],
    modifiers: [u8; 2],
    remainder: [f32; 2],
    #[cfg(target_os = "macos")]
    scroll: crate::scroll::PixelScroll,
    dry: bool,
}
impl Input {
    pub fn new(dry: bool) -> Self {
        Self {
            held: [false; 3],
            modifiers: [0; 2],
            remainder: [0.0; 2],
            #[cfg(target_os = "macos")]
            scroll: crate::scroll::PixelScroll::default(),
            dry,
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
                    native::motion(x, y, self.modifiers)?;
                }
            }
            2 => {
                let i = p.a as usize;
                let down = p.b == 1.0;
                if self.held[i] != down {
                    if !self.dry {
                        native::button(i, down, self.modifiers)?;
                    }
                    self.held[i] = down;
                }
            }
            3 => {
                #[cfg(target_os = "macos")]
                {
                    let (x, y) = self.scroll.take(p.a, p.b);
                    if !self.dry && (x != 0 || y != 0) {
                        native::scroll(x, y, self.modifiers)?;
                    }
                }
                #[cfg(not(target_os = "macos"))]
                {
                    if !self.dry {
                        native::scroll(p.a, p.b, self.modifiers)?;
                    }
                }
            }
            4 => {
                if !self.dry {
                    native::shortcut(p.a as u8, self.modifiers)?;
                }
            }
            6 => self.release(),
            8 => self.key(p.a as u8, p.b as i8)?,
            _ => (),
        }
        Ok(())
    }
    fn key(&mut self, usage: u8, action: i8) -> Result<()> {
        if action == 0 {
            if !self.dry && !self.modifiers.contains(&usage) {
                native::key(usage, true, self.modifiers)?;
                native::key(usage, false, self.modifiers)?;
            }
            return Ok(());
        }

        let slot = usize::from(action.unsigned_abs() - 1);
        let down = action > 0;
        let current = self.modifiers[slot];
        if (down && current != 0) || (!down && current != usage) {
            return Ok(());
        }
        let mut next = self.modifiers;
        next[slot] = if down { usage } else { 0 };
        if !self.dry && self.modifiers.contains(&usage) != next.contains(&usage) {
            native::key(usage, down, next)?;
        }
        self.modifiers = next;
        Ok(())
    }
    pub fn release(&mut self) {
        for i in 0..3 {
            if self.held[i] {
                if !self.dry {
                    let _ = native::button(i, false, self.modifiers);
                }
                self.held[i] = false;
            }
        }
        for slot in 0..2 {
            let usage = self.modifiers[slot];
            if usage != 0 {
                let _ = self.key(usage, -(slot as i8 + 1));
            }
        }
        self.modifiers = [0; 2];
        self.remainder = [0.0; 2];
        #[cfg(target_os = "macos")]
        {
            self.scroll = crate::scroll::PixelScroll::default();
        }
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
    use crate::keyboard;
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
    pub fn motion(x: i32, y: i32, _: [u8; 2]) -> Result<()> {
        mouse(x, y, 0, 0x0001)
    }
    pub fn button(i: usize, down: bool, _: [u8; 2]) -> Result<()> {
        mouse(0, 0, 0, [[2, 4], [8, 16], [32, 64]][i][usize::from(!down)])
    }
    pub fn scroll(x: f32, y: f32, _: [u8; 2]) -> Result<()> {
        if y != 0.0 {
            mouse(0, 0, (-y * 3.0).round() as i32 as u32, 0x0800)?;
        }
        if x != 0.0 {
            mouse(0, 0, (x * 3.0).round() as i32 as u32, 0x1000)?;
        }
        Ok(())
    }
    pub fn shortcut(id: u8, _: [u8; 2]) -> Result<()> {
        let vk = [0xA6, 0xA7, 0xB3, 0xAF, 0xAE, 0x0D, 0x1B, 0x09, 0x08][id as usize];
        send_key(vk, false, true)?;
        send_key(vk, false, false)
    }
    pub fn key(usage: u8, down: bool, _: [u8; 2]) -> Result<()> {
        let (vk, extended) = keyboard::windows_key(usage);
        send_key(vk, extended, down)
    }
    fn send_key(vk: u16, extended: bool, down: bool) -> Result<()> {
        let e = Event {
            kind: 1,
            payload: Payload {
                key: Key {
                    vk,
                    scan: 0,
                    flags: u32::from(extended) | (u32::from(!down) << 1),
                    time: 0,
                    extra: 0,
                },
            },
        };
        if unsafe { SendInput(1, &e, std::mem::size_of::<Event>() as i32) } != 1 {
            bail!("keyboard injection rejected");
        }
        Ok(())
    }
}
#[cfg(target_os = "macos")]
mod native {
    use super::*;
    use crate::keyboard;
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
        // Use Apple's non-variadic API. The old declaration omitted the fixed
        // wheel1 argument; on Apple Silicon this placed it on the stack instead
        // of in the expected register, producing arbitrary scroll magnitudes.
        fn CGEventCreateScrollWheelEvent2(
            source: *const c_void,
            units: u32,
            count: u32,
            wheel1: i32,
            wheel2: i32,
            wheel3: i32,
        ) -> *mut c_void;
        #[cfg(test)]
        fn CGEventGetIntegerValueField(event: *const c_void, field: u32) -> i64;
        #[cfg(test)]
        fn CGEventGetType(event: *const c_void) -> u32;
        #[cfg(test)]
        fn CGEventGetFlags(event: *const c_void) -> u64;
        fn CGEventCreateKeyboardEvent(source: *const c_void, key: u16, down: bool) -> *mut c_void;
        fn CGEventSetType(event: *mut c_void, kind: u32);
        fn CGEventSetFlags(event: *mut c_void, flags: u64);
        fn CGEventSourceFlagsState(state: i32) -> u64;
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
    fn modifier_flags(usage: u8) -> (u64, u64) {
        match usage {
            0xE0 => (1 << 18, 0x0001), // Left Control
            0xE1 => (1 << 17, 0x0002), // Left Shift
            0xE2 => (1 << 19, 0x0020), // Left Option
            0xE3 => (1 << 20, 0x0008), // Left Command
            0xE4 => (1 << 18, 0x2000), // Right Control
            0xE5 => (1 << 17, 0x0004), // Right Shift
            0xE6 => (1 << 19, 0x0040), // Right Option
            0xE7 => (1 << 20, 0x0010), // Right Command
            _ => (0, 0),
        }
    }
    fn held_flags(held: [u8; 2]) -> u64 {
        held.into_iter().fold(0, |flags, usage| {
            let (group, side) = modifier_flags(usage);
            flags | group | side
        })
    }
    fn post_with_modifiers(e: *mut c_void, held: [u8; 2]) -> Result<()> {
        if !e.is_null() && held != [0; 2] {
            unsafe { CGEventSetFlags(e, CGEventSourceFlagsState(1) | held_flags(held)) };
        }
        post(e)
    }
    pub fn motion(x: i32, y: i32, held: [u8; 2]) -> Result<()> {
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
            post_with_modifiers(
                CGEventCreateMouseEvent(
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
                ),
                held,
            )
        }
    }
    pub fn button(i: usize, down: bool, held: [u8; 2]) -> Result<()> {
        unsafe {
            post_with_modifiers(
                CGEventCreateMouseEvent(
                    std::ptr::null(),
                    [[1, 2], [3, 4], [25, 26]][i][usize::from(!down)],
                    point(),
                    i as u32,
                ),
                held,
            )
        }
    }
    fn scroll_event(x: i32, y: i32) -> Result<*mut c_void> {
        let event = unsafe { CGEventCreateScrollWheelEvent2(std::ptr::null(), 0, 2, -y, -x, 0) };
        if event.is_null() {
            bail!("macOS scroll event unavailable");
        }
        Ok(event)
    }
    pub fn scroll(x: i32, y: i32, held: [u8; 2]) -> Result<()> {
        post_with_modifiers(scroll_event(x, y)?, held)
    }
    pub fn shortcut(id: u8, held: [u8; 2]) -> Result<()> {
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
                    CGEventSetFlags(event, (1 << 20) | held_flags(held));
                    post(event)?;
                } else {
                    post_with_modifiers(event, held)?;
                }
            }
        }
        Ok(())
    }
    const FLAGS_CHANGED: u32 = 12;
    const CAPS_LOCK: u8 = 0x39;

    fn keyboard_event(usage: u8, down: bool, mut flags: u64) -> Result<*mut c_void> {
        let (group, side) = modifier_flags(usage);
        if down {
            flags |= group | side;
        } else if group != 0 {
            flags &= !side;
            let (_, other_side) = modifier_flags(usage ^ 4);
            if flags & other_side == 0 {
                flags &= !group;
            }
        }
        let event =
            unsafe { CGEventCreateKeyboardEvent(std::ptr::null(), keyboard::mac_key(usage), down) };
        if event.is_null() {
            bail!("macOS keyboard event unavailable");
        }
        unsafe {
            if group != 0 || usage == CAPS_LOCK {
                CGEventSetType(event, FLAGS_CHANGED);
            }
            CGEventSetFlags(event, flags);
        }
        Ok(event)
    }
    pub fn key(usage: u8, down: bool, held: [u8; 2]) -> Result<()> {
        let mut flags = unsafe { CGEventSourceFlagsState(1) } | held_flags(held);
        if usage == CAPS_LOCK {
            if !down {
                return Ok(());
            }
            let caps = 1 << 16;
            let current = unsafe { CGEventSourceFlagsState(0) } & caps;
            flags = (flags & !caps) | (current ^ caps);
        }
        post(keyboard_event(usage, down, flags)?)
    }
    #[cfg(test)]
    mod tests {
        use super::*;

        #[test]
        fn quartz_right_option_events_identify_the_correct_side() {
            let left_option = (1 << 19) | 0x20;
            let right_option = (1 << 19) | 0x40;
            for (down, flags, expected) in [
                (true, 0, right_option),
                (false, right_option, 0),
                (false, right_option | left_option, left_option),
            ] {
                let event = keyboard_event(0xE6, down, flags).unwrap();
                unsafe {
                    let keycode = CGEventGetIntegerValueField(event, 9);
                    let kind = CGEventGetType(event);
                    let flags = CGEventGetFlags(event);
                    CFRelease(event);
                    assert_eq!(keycode, 0x3D);
                    assert_eq!(kind, FLAGS_CHANGED);
                    assert_eq!(flags, expected);
                }
            }
        }

        #[test]
        fn quartz_scroll_event_preserves_signed_pixel_axes_without_posting() {
            for (x, y) in [
                (0, 1),
                (0, -1),
                (1, 0),
                (-1, 0),
                (3, -7),
                (-4, 9),
                (2048, -2048),
            ] {
                let event = scroll_event(x, y).expect("create scroll event");
                let (vertical, horizontal, continuous) = unsafe {
                    let fields = (
                        CGEventGetIntegerValueField(event, 96), // point delta axis 1 (vertical)
                        CGEventGetIntegerValueField(event, 97), // point delta axis 2 (horizontal)
                        CGEventGetIntegerValueField(event, 88), // pixel/continuous flag
                    );
                    CFRelease(event);
                    fields
                };
                assert_eq!(vertical, i64::from(-y), "vertical delta for ({x}, {y})");
                assert_eq!(horizontal, i64::from(-x), "horizontal delta for ({x}, {y})");
                assert_eq!(continuous, 1, "pixel units must remain continuous");
            }
        }
    }
}
#[cfg(not(any(target_os = "windows", target_os = "macos")))]
mod native {
    use super::*;
    pub fn motion(_: i32, _: i32, _: [u8; 2]) -> Result<()> {
        bail!("unsupported OS")
    }
    pub fn button(_: usize, _: bool, _: [u8; 2]) -> Result<()> {
        bail!("unsupported OS")
    }
    pub fn scroll(_: f32, _: f32, _: [u8; 2]) -> Result<()> {
        bail!("unsupported OS")
    }
    pub fn shortcut(_: u8, _: [u8; 2]) -> Result<()> {
        bail!("unsupported OS")
    }
    pub fn key(_: u8, _: bool, _: [u8; 2]) -> Result<()> {
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
    fn modifier_slots_release_independently_and_clear_on_disconnect() {
        let mut input = Input::new(true);
        input.key(0xE6, 1).unwrap();
        input.key(0xE6, 2).unwrap();
        input.key(0xE2, -1).unwrap();
        assert_eq!(input.modifiers, [0xE6, 0xE6]);
        input.key(0xE6, -1).unwrap();
        assert_eq!(input.modifiers, [0, 0xE6]);
        input.release();
        assert_eq!(input.modifiers, [0, 0]);
    }
    #[cfg(target_os = "macos")]
    #[test]
    fn release_resets_fractional_scroll_before_the_next_gesture() {
        let mut input = Input::new(true);
        input
            .dispatch(Packet {
                kind: 3,
                sequence: 1,
                micros: 0,
                a: 0.75,
                b: -0.75,
            })
            .unwrap();
        input.release();
        assert_eq!(input.scroll.take(0.25, -0.25), (0, 0));
        assert_eq!(input.scroll.take(0.75, -0.75), (1, -1));
    }
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
