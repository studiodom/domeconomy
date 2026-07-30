package domeconomy.listener;

import java.util.UUID;

public record BlockLoc(UUID worldUid, int x, int y, int z) {}