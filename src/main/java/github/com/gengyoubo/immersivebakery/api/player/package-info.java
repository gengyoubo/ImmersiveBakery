/**
 * Work done in the player's own hands, anywhere: no block, no menu. The camera stays at the player's eyes and only looks down a
 * little; the tool and what is worked on are drawn before it, and other players see the arms move.
 *
 * <ul>
 * <li>Common setup: {@link github.com.gengyoubo.immersivebakery.api.player.PlayerImmersion#register} a server
 * {@link github.com.gengyoubo.immersivebakery.api.player.PlayerImmersion.Session}. It alone checks and changes items.
 * <li>Client setup: {@link github.com.gengyoubo.immersivebakery.api.player.PlayerImmersionClient#register} a
 * {@link github.com.gengyoubo.immersivebakery.api.player.PlayerImmersionScreen} for the same id, and
 * {@link github.com.gengyoubo.immersivebakery.api.player.PlayerImmersionPoses#register} what others see.
 * <li>Open it with {@link github.com.gengyoubo.immersivebakery.api.player.PlayerImmersionClient#requestOpen} or, on the server,
 * {@link github.com.gengyoubo.immersivebakery.api.player.PlayerImmersion#open}.
 * </ul>
 */
package github.com.gengyoubo.immersivebakery.api.player;
