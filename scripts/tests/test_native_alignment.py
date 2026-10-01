import struct
import unittest

from scripts.check_native_alignment import check_elf


def elf(alignment=16384, address=0, kind=1):
    data = bytearray(120)
    data[:6] = b"\x7fELF\x02\x01"
    struct.pack_into("<Q", data, 32, 64)
    struct.pack_into("<HH", data, 54, 56, 1)
    struct.pack_into("<IIQQQQQQ", data, 64, kind, 5, 0, address, 0, 120, 120, alignment)
    return bytes(data)


class NativeAlignmentTest(unittest.TestCase):
    def test_aligned(self):
        check_elf(elf())
        check_elf(elf(65536))

    def test_four_k_and_non_power_of_two_rejected(self):
        for alignment in (0, 4096, 24576):
            with self.assertRaises(ValueError):
                check_elf(elf(alignment))

    def test_misaligned_address_rejected(self):
        with self.assertRaises(ValueError):
            check_elf(elf(address=4096))

    def test_malformed_or_no_load_rejected(self):
        for data in (b"", elf()[:80], elf(kind=2)):
            with self.assertRaises(ValueError):
                check_elf(data)
