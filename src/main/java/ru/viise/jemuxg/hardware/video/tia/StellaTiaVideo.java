package ru.viise.jemuxg.hardware.video.tia;

import ru.viise.jemuxg.api.debug.StateReader;
import ru.viise.jemuxg.api.debug.StateWriter;
import ru.viise.jemuxg.api.hardware.AudioSink;
import ru.viise.jemuxg.api.hardware.Bus;
import ru.viise.jemuxg.api.hardware.Button;
import ru.viise.jemuxg.api.hardware.Controller;
import ru.viise.jemuxg.api.hardware.PaddleController;
import ru.viise.jemuxg.api.hardware.VideoFrame;
import ru.viise.jemuxg.api.hardware.VideoSink;
import ru.viise.jemuxg.console.atari2600.Atari2600Constants;
import ru.viise.jemuxg.hardware.audio.tia.TiaAudio;

/**
 * Адаптер {@link StellaTia} к контракту машины.
 *
 * <p>Добавляет видео/аудиовыход, джойстики, paddle, снимок регистров и
 * сохранение состояния. Используется как основной TIA Atari 2600.</p>
 *
 * <p>Ядро портировано из проекта
 * <a href="https://github.com/stella-emu/stella">Stella</a>.
 * Особая благодарность авторам и участникам Stella.</p>
 */
public final class StellaTiaVideo implements AtariTiaDevice {

    private static final int STATE_MAGIC = 0x53544941;
    private static final int STATE_VERSION = 2;
    private static final double PADDLE_MAX_RESISTANCE = 1_000_000.0;
    private static final double PADDLE_R0 = 1_800.0;
    private static final double PADDLE_CAPACITANCE = 68e-9;
    private static final double PADDLE_DUMP_RESISTANCE = 50.0;
    private static final double PADDLE_SUPPLY = 5.0;
    private static final double PADDLE_TRIPPOINT_LINES = 379.0;
    private static final double PADDLE_CLOCK_FREQ = 60.0 * 228.0 * 262.0;
    private static final double PADDLE_THRESHOLD = PADDLE_SUPPLY
            * (1.0 - Math.exp(-PADDLE_TRIPPOINT_LINES * 228.0 / PADDLE_CLOCK_FREQ
                    / (PADDLE_MAX_RESISTANCE + PADDLE_R0) / PADDLE_CAPACITANCE));

    private final StellaTia tia = new StellaTia();
    private final TiaAudio audio = new TiaAudio();
    private final Controller[] controllers = new Controller[2];
    private final PaddleController[] paddles = new PaddleController[4];
    private final double[] paddleVoltage = new double[4];
    private final long[] paddleTimestamp = new long[4];
    private final int[] paddleResistance = new int[4];
    private final boolean[] paddleDumped = new boolean[4];
    private final Bus registers = new Bus() {
        @Override
        public int read(int address) {
            return readRegister(address & 0x3F);
        }

        @Override
        public void write(int address, int value) {
            writeRegister(address & 0x3F, value);
        }
    };

    private VideoSink sink;

    public StellaTiaVideo() {
        reset();
    }

    public StellaTia core() {
        return tia;
    }

    @Override
    public String name() {
        return "Stella TIA";
    }

    @Override
    public void reset() {
        tia.reset();
        audio.reset();
        for (int i = 0; i < paddles.length; i++) {
            paddleVoltage[i] = 0.0;
            paddleTimestamp[i] = 0;
            paddleResistance[i] = 0;
            paddleDumped[i] = false;
        }
    }

    @Override
    public Bus registers() {
        return registers;
    }

    @Override
    public void connect(VideoSink sink) {
        this.sink = sink;
    }

    @Override
    public void connectAudio(AudioSink sink) {
        audio.connect(sink);
    }

    @Override
    public void connectPlayer(int port, Controller controller) {
        if (port < 0 || port >= controllers.length) {
            throw new IllegalArgumentException("Invalid Atari player port: " + port);
        }
        controllers[port] = controller;
    }

    @Override
    public void connectPaddle(int port, PaddleController paddle) {
        if (port < 0 || port >= paddles.length) {
            throw new IllegalArgumentException("Invalid Atari paddle port: " + port);
        }
        paddles[port] = paddle;
    }

    @Override
    public int width() {
        return tia.width();
    }

    @Override
    public int height() {
        return tia.height();
    }

    @Override
    public int scanline() {
        return tia.scanline();
    }

    @Override
    public int colorClock() {
        return tia.hctr();
    }

    @Override
    public boolean cpuHalted() {
        return tia.cpuHalted();
    }

    @Override
    public long frameCount() {
        return tia.frame();
    }

    @Override
    public int[] framebufferArgb() {
        return tia.framebufferArgb();
    }

    @Override
    public byte[] framebufferIndices() {
        return tia.framebufferIndices();
    }

    @Override
    public byte[] registerSnapshot() {
        byte[] snapshot = tia.registerSnapshot();
        snapshot[0x15] = (byte) audio.registerValue(0x15);
        snapshot[0x16] = (byte) audio.registerValue(0x16);
        snapshot[0x17] = (byte) audio.registerValue(0x17);
        snapshot[0x18] = (byte) audio.registerValue(0x18);
        snapshot[0x19] = (byte) audio.registerValue(0x19);
        snapshot[0x1A] = (byte) audio.registerValue(0x1A);
        snapshot[0x3C] = (byte) fireInput(0);
        snapshot[0x3D] = (byte) fireInput(1);
        return snapshot;
    }

    @Override
    public void tick(long colorClocks) {
        if (colorClocks < 0) {
            throw new IllegalArgumentException("colorClocks must be >= 0");
        }
        for (long i = 0; i < colorClocks; i++) {
            long frame = tia.frame();
            tia.tick(1);
            audio.tick(1);
            if (tia.frame() != frame) {
                audio.flush();
                emitFrame();
            }
        }
    }

    @Override
    public void save(StateWriter writer) {
        writer.writeInt(STATE_MAGIC);
        writer.writeByte(STATE_VERSION);
        tia.save(writer);
        audio.save(writer);
        for (int i = 0; i < paddles.length; i++) {
            writer.writeLong(Double.doubleToLongBits(paddleVoltage[i]));
            writer.writeLong(paddleTimestamp[i]);
            writer.writeInt(paddleResistance[i]);
            writer.writeByte(paddleDumped[i] ? 1 : 0);
        }
    }

    @Override
    public void restore(StateReader reader) {
        if (reader.readInt() != STATE_MAGIC) {
            throw new IllegalArgumentException("invalid Stella TIA save state");
        }
        int version = reader.readByte();
        if (version != STATE_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported Stella TIA save state version: " + version);
        }
        tia.restore(reader);
        audio.restore(reader);
        for (int i = 0; i < paddles.length; i++) {
            paddleVoltage[i] = Double.longBitsToDouble(reader.readLong());
            paddleTimestamp[i] = reader.readLong();
            paddleResistance[i] = reader.readInt();
            paddleDumped[i] = reader.readByte() != 0;
        }
    }

    private void writeRegister(int register, int value) {
        tia.registers().write(register, value);
        audio.writeRegister(register, value);
        if (register == 0x01) {
            updatePaddleDump(value);
        }
    }

    private int readRegister(int register) {
        if (register >= 0x15 && register <= 0x1A) {
            return audio.registerValue(register);
        }
        if (register >= 0x08 && register <= 0x0B) {
            return paddleInput(register - 0x08);
        }
        if (register >= 0x38 && register <= 0x3B) {
            return paddleInput(register - 0x38);
        }
        if (register == 0x0C || register == 0x3C) {
            return fireInput(0);
        }
        if (register == 0x0D || register == 0x3D) {
            return fireInput(1);
        }
        return tia.registers().read(register);
    }

    private int fireInput(int port) {
        Controller controller = controllers[port];
        return controller != null && controller.pressed(Button.A) ? 0x00 : 0x80;
    }

    private int paddleInput(int index) {
        PaddleController paddle = paddles[index];
        if (paddle == null) {
            return 0x80;
        }
        long now = tia.colorClock();
        updatePaddleCharge(index, now);
        int resistance = (int) (PADDLE_MAX_RESISTANCE
                * paddle.paddlePosition() / 1000.0);
        paddleResistance[index] = Math.max(0, resistance);
        boolean high = !paddleDumped[index]
                && paddleVoltage[index] > PADDLE_THRESHOLD;
        return high ? 0x80 : 0x00;
    }

    private void updatePaddleCharge(int index, long now) {
        long elapsed = now - paddleTimestamp[index];
        if (elapsed <= 0) {
            return;
        }
        double voltage = paddleVoltage[index];
        double tau;
        if (paddleDumped[index]) {
            tau = PADDLE_DUMP_RESISTANCE * PADDLE_CAPACITANCE;
        } else if (paddles[index] == null) {
            paddleTimestamp[index] = now;
            return;
        } else {
            int resistance = paddleResistance[index];
            if (resistance == 0) {
                resistance = (int) (PADDLE_MAX_RESISTANCE
                        * paddles[index].paddlePosition() / 1000.0);
                paddleResistance[index] = Math.max(0, resistance);
            }
            tau = (resistance + PADDLE_R0) * PADDLE_CAPACITANCE;
        }
        double exponent = -elapsed / tau / PADDLE_CLOCK_FREQ;
        if (paddleDumped[index]) {
            paddleVoltage[index] = voltage * Math.exp(exponent);
        } else {
            paddleVoltage[index] = PADDLE_SUPPLY
                    - (PADDLE_SUPPLY - voltage) * Math.exp(exponent);
        }
        paddleTimestamp[index] = now;
    }

    private void updatePaddleDump(int value) {
        long now = tia.colorClock();
        for (int i = 0; i < paddles.length; i++) {
            updatePaddleCharge(i, now);
            paddleDumped[i] = (value & 0x80) != 0;
            paddleTimestamp[i] = now;
        }
    }

    private void emitFrame() {
        if (sink != null) {
            sink.frameReady(new VideoFrame(
                    Atari2600Constants.VISIBLE_WIDTH,
                    Atari2600Constants.VISIBLE_HEIGHT,
                    tia.framebufferArgb()));
        }
    }
}
