package backend.camp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A per-placement prize for a camp. Stored as a JSON element inside the camp
 * row ({@code rewards} jsonb) — no child table, single-row fetch.
 *
 * <p>{@code rank} is 1/2/3 (1st/2nd/3rd). {@code cosmeticId} is the
 * {@code cosmetic.id} granted to every linked participant who places at that rank
 * when the admin saves results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampReward {
    private int rank;
    private String cosmeticId;
}
