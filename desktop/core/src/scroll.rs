/// Preserve subpixel scroll travel until it adds up to a whole Quartz pixel.
/// Owned by the input session, so fractions cannot leak into a later connection.
#[derive(Default)]
pub(crate) struct PixelScroll {
    remainder: [f32; 2],
}

impl PixelScroll {
    pub(crate) fn take(&mut self, x: f32, y: f32) -> (i32, i32) {
        let mut pixels = [0; 2];
        for (axis, value) in [x, y].into_iter().enumerate() {
            if !value.is_finite() {
                self.remainder[axis] = 0.0;
                continue;
            }
            let total = self.remainder[axis] + value.clamp(-2048.0, 2048.0);
            pixels[axis] = total.trunc() as i32;
            self.remainder[axis] = total - pixels[axis] as f32;
        }
        (pixels[0], pixels[1])
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn small_movements_accumulate_without_becoming_large_steps() {
        let mut s = PixelScroll::default();
        for _ in 0..3 {
            assert_eq!(s.take(0.25, -0.25), (0, 0));
        }
        assert_eq!(s.take(0.25, -0.25), (1, -1));
        assert_eq!(s.take(0.0, 0.0), (0, 0));
    }

    #[test]
    fn signs_axes_and_total_travel_are_preserved() {
        let mut s = PixelScroll::default();
        let mut sum = (0, 0);
        for _ in 0..100 {
            let (x, y) = s.take(-0.25, 0.5);
            sum.0 += x;
            sum.1 += y;
            assert!(x.abs() <= 1 && y.abs() <= 1);
        }
        assert_eq!(sum, (-25, 50));
    }

    #[test]
    fn zero_invalid_and_out_of_range_input_cannot_create_a_page_jump() {
        let mut s = PixelScroll::default();
        assert_eq!(s.take(0.0, 0.0), (0, 0));
        assert_eq!(s.take(f32::NAN, f32::INFINITY), (0, 0));
        assert_eq!(s.take(f32::MAX, -f32::MAX), (2048, -2048));
    }

    #[test]
    fn opposite_travel_cancels_and_a_new_session_has_no_remainder() {
        let mut s = PixelScroll::default();
        assert_eq!(s.take(0.75, -0.75), (0, 0));
        assert_eq!(s.take(-0.75, 0.75), (0, 0));
        assert_eq!(s.take(0.75, -0.75), (0, 0));
        s = PixelScroll::default();
        assert_eq!(s.take(0.25, -0.25), (0, 0));
    }
}
