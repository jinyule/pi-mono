package dev.pi.tools;

@FunctionalInterface
public interface BashSpawnHook {
    BashSpawnContext apply(BashSpawnContext context);
}
