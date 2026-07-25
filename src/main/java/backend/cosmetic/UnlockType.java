package backend.cosmetic;

/**
 * How a cosmetic becomes equippable:
 * <ul>
 *   <li>{@code BELT} — unlocked automatically when the user's belt rank reaches
 *       {@code unlockBelt}'s rank (the legacy CSS belt cosmetics).</li>
 *   <li>{@code EVENT_REWARD} — granted when a linked user places in an event.</li>
 *   <li>{@code CAMP_REWARD} — granted when a linked user places in a camp.</li>
 * </ul>
 */
public enum UnlockType {
    BELT,
    EVENT_REWARD,
    CAMP_REWARD
}
