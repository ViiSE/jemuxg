# TODO

- [x] Реализовать набор инструкций `Mos6502` (сейчас только регистры и reset).
- [x] Разобрать iNES-заголовок и реализовать мапперы NROM/MMC1/MMC3 в `NesCartridgeSlot`.
- [x] Сделать реальный рендеринг PPU и каналы APU.
- [x] Добавить фронтенд: `javax.sound.sampled` + Swing, ввод с клавиатуры.
- [x] Добавить тесты на контракты и тестовые ROM (nestest, blargg).
- [x] Тесты PPU: рендеринг фона и спрайтов, mirroring, NMI, sprite 0 hit, save/restore.
- [x] Тесты APU: каналы, frame counter, IRQ, DC removal, save/restore.
- [x] Тесты мапперов: NROM/MMC1/MMC3, PRG/CHR banking, PRG RAM, IRQ.
- [x] Тесты ввода: опрос `$4016/$4017`, strobe, сброс, два порта.
- [x] Исправить `vbl_clear_time`: флаг VBL должен сбрасываться примерно через 2270 CPU-тактов после NMI.
- [x] Повторить начальную палитру Blargg (`power_up_palette`).
- [x] Добавить CNROM (mapper 3) и автоматизировать `test_ppu_read_buffer`.
