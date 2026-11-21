# TODO: Stella-совместимый TIA

Новый TIA — отдельный порт Stella. Старый упрощённый TIA удалён,
`StellaTiaVideo` используется по умолчанию.

- [x] 1. `StellaTia` core:
  - 228 цветовых тактов на строку;
  - 262 строки NTSC;
  - `hctr`, `hblank/frame`, `scanline`, `frame`;
  - `catchUpTo(colorClock)`;
  - `DelayQueue` с точными задержками регистров;
  - `nextLine()`, `VSYNC`/`VBLANK`;
  - `RESP` target 157/158/159;
  - `GRP` + VDEL cross-shuffle P0/P1;
  - изолированные тесты `StellaTiaTests`.

- [x] 1.1. `StellaTiaPlayer`:
  - `counter` 0..159;
  - `renderCounter`, `renderCounterTripPoint`;
  - `sampleCounter`, `divider`, `copy`;
  - NUSIZ decode table;
  - `grp`, `shufflePatterns`, `vdel`, `refp`;
  - `resp`, `tick`, `tickInHblank`;
  - изолированные тесты `StellaTiaPlayerTests`.

- [x] 2. `StellaTiaPlayfield`:
  - PF0/PF1/PF2;
  - 20-битный pattern;
  - `CTRLPF`: reflect, score mode, priority;
  - latch `refp` в x=0/79;
  - задержка PF 2 такта;
  - тесты формы, отражения и цветов
    (`StellaTiaPlayfieldTests`, checks=34);
  - подключён к изолированному `StellaTia` (PF/CTRLPF/COLUPx,
    `tick` в видимой области, `nextLine`).

- [x] 3. `StellaTiaMissile` и `StellaTiaBall`:
  - `RESM0/1`, `RESBL`, target 157/159 (158 — после HMOVE);
  - NUSIZ и CTRLPF размеры 1/2/4/8;
  - ENAM0/1, ENABL, VDELBL + shuffle от GRP1;
  - HMP/HMM/HMBL и формула `(value >> 4) ^ 0x08`;
  - RESMP и starfield-логика;
  - задержки: ENAM/ENABL 1, HMM/HMBL/HMP/HMCLR 2;
  - подключены к `StellaTia` (NUSIZ/CTRLPF/COLUPx/RESx/tick/nextLine);
  - тесты `StellaTiaMissileTests` (checks=23) и
    `StellaTiaBallTests` (checks=18).

- [x] 4. `StellaTia` frame output:
  - framebuffer 160×192 (индексный + ARGB);
  - приоритеты PF/P0/M0/P1/M1/BL/BK (normal, score, PFP);
  - цвета `COLUP0/1/PF/BK` с маской `& 0xFE`;
  - VBLANK и окно строк 40..231;
  - очистка буфера на старте следующего кадра;
  - тесты `StellaTiaFrameTests` (checks=15);
  - полное сравнение с кадрами Pitfall II — после подключения
    объектов/HMOVE, см. пункт 6.

- [x] 5. HMOVE:
  - задержка 6 тактов;
  - extended HBLANK 8 тактов (видимая область с hctr 76);
  - movement ticks каждые 4 такта;
  - HMP/HMM/HMBL формула `(value >> 4) ^ 0x08`;
  - движение player/missile/ball в HBLANK;
  - HMCLR сбрасывает движение к 8 тактам;
  - comb очищает первые 8 пикселей строки;
  - late HMOVE: движение вне HBLANK не добавляется;
  - тесты `StellaTiaHmoveTests` (checks=28).

- [x] 6. Сравнение с Stella:
  - кадры Pitfall II 49 и 50 сравниваются со старыми эталонами
    по содержимому (без x=0..7, где старый TIA не рисует HMOVE comb);
  - допуск 5% по числу непрозрачных пикселей, `StellaTiaReferenceTests`;
  - `Copyright 1983`, включая descender `y/p`, проверяется отдельно;
  - кадр 567 в старом дампе снят после ввода/старта игры, поэтому
    в детерминированном прогоне без ввода проверяется как smoke-кадр
    (полный кадр, без сравнения пикселей);
  - движение объектов покрыто тестами `StellaTiaHmoveTests`
    (HMP/HMM/HMBL, extended HBLANK, late HMOVE);
  - побайтовое совпадение со старым TIA недостижимо: старый слой
    не реализует comb и рисует игроков по RESP, а не по counter/decode.

- [x] 7. Подключение нового TIA:
  - `AtariTiaDevice` + `StellaTiaVideo` адаптер (видео, аудио,
    джойстики, save/restore, snapshot);
  - `StellaTiaVideo` — единственный TIA и используется по умолчанию;
  - входы читаются в обоих зеркалах: `$08..$0D` и `$38..$3D`;
  - fire (`INPT4/5`) и аналоговые paddle-входы (`INPT0..3`)
    подключены к контроллерам;
  - коллизии читаются в обоих зеркалах: `$00..$07` и `$30..$37`;
  - save state версии 3, без байта типа TIA; старые состояния
    версии 2 отклоняются;
  - тесты `StellaTiaMachineTests` (checks=17).

- [x] 7.1. Удаление старого слоя и атрибуция:
  - удалены `Tia.java` и `TiaPlayerState.java`;
  - `Atari2600Machine` больше не содержит feature flag, старый
    конструктор с `boolean` удалён;
  - `AtariDebugger` использует константы `StellaTia`;
  - в javadoc `StellaTia`, `StellaTiaPlayer`, `StellaTiaMissile`,
    `StellaTiaBall`, `StellaTiaPlayfield`, `StellaTiaVideo` добавлена
    ссылка на проект Stella и благодарность авторам;
  - в `README.md` обновлён раздел Atari 2600, добавлен раздел
    «Благодарности» со ссылкой https://github.com/stella-emu/stella;
  - `AtariTiaTests` переписаны под контракт нового слоя (checks=30);
  - `AtariTiaAudioTests` переведены на `StellaTia` (checks=26).

- [ ] 8. Финальная проверка:
  - `mvn clean package` — проходит;
  - ручной запуск Pitfall II — проверен;
  - Kaboom (paddle и коллизии) — проверен;
  - проверить F10/↑/↓/F12;
  - отсутствие регрессий в остальных играх.
