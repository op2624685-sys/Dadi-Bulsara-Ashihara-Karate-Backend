package backend.user;

/**
 * The pair of cosmetic ids a person has equipped (avatar + banner). Returned by
 * the directory/profile services so the response can surface the equipped look
 * that lives on the owning {@link UserEntity}. {@link #NONE} is used when the
 * underlying record has no linked account (e.g. a seed row).
 */
public record EquippedCosmetics(String avatarId, String bannerId) {

    public static final EquippedCosmetics NONE = new EquippedCosmetics(null, null);
}
