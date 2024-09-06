package org.mastodon.views.bvv;

import static bdv.BigDataViewerActions.COLLAPSE_CARDS;
import static bdv.BigDataViewerActions.COLLAPSE_CARDS_KEYS;
import static bdv.BigDataViewerActions.EXPAND_CARDS;
import static bdv.BigDataViewerActions.EXPAND_CARDS_KEYS;
import static bdv.BigDataViewerActions.GO_TO_BOOKMARK;
import static bdv.BigDataViewerActions.GO_TO_BOOKMARK_KEYS;
import static bdv.BigDataViewerActions.GO_TO_BOOKMARK_ROTATION;
import static bdv.BigDataViewerActions.GO_TO_BOOKMARK_ROTATION_KEYS;
import static bdv.BigDataViewerActions.LOAD_SETTINGS;
import static bdv.BigDataViewerActions.LOAD_SETTINGS_KEYS;
import static bdv.BigDataViewerActions.SAVE_SETTINGS;
import static bdv.BigDataViewerActions.SAVE_SETTINGS_KEYS;
import static bdv.BigDataViewerActions.SET_BOOKMARK;
import static bdv.BigDataViewerActions.SET_BOOKMARK_KEYS;
import static bdv.BigDataViewerActions.bookmarks;

import org.scijava.plugin.Plugin;
import org.scijava.ui.behaviour.io.gui.CommandDescriptionProvider;
import org.scijava.ui.behaviour.io.gui.CommandDescriptions;
import org.scijava.ui.behaviour.util.Actions;

import bvv.core.BigVolumeViewerActions;
import bvv.core.KeyConfigContexts;
import bvv.core.KeyConfigScopes;

/**
 * Copied from {@link BigVolumeViewerActions}.
 */
public class BigVolumeViewerActionsMamut
{
	/**
	 * The descriptions are re-declared here with scope
	 * {@link KeyConfigScopes#BIGVOLUMEVIEWER}.
	 */
	@Plugin( type = CommandDescriptionProvider.class )
	public static class Descriptions extends CommandDescriptionProvider
	{
		public Descriptions()
		{
			super( KeyConfigScopes.BIGVOLUMEVIEWER, KeyConfigContexts.BIGVOLUMEVIEWER );
		}

		@Override
		public void getCommandDescriptions( final CommandDescriptions descriptions )
		{
			// Commands re-used from bdv.BigDataViewerActions
			descriptions.add( SAVE_SETTINGS, SAVE_SETTINGS_KEYS, "Save the BigDataViewer settings to a settings.xml file." );
			descriptions.add( LOAD_SETTINGS, LOAD_SETTINGS_KEYS, "Load the BigDataViewer settings from a settings.xml file." );
			descriptions.add( EXPAND_CARDS, EXPAND_CARDS_KEYS, "Expand and focus the BigDataViewer card panel" );
			descriptions.add( COLLAPSE_CARDS, COLLAPSE_CARDS_KEYS, "Collapse the BigDataViewer card panel" );
			descriptions.add( SET_BOOKMARK, SET_BOOKMARK_KEYS, "Set a labeled bookmark at the current location." );
			descriptions.add( GO_TO_BOOKMARK, GO_TO_BOOKMARK_KEYS, "Retrieve a labeled bookmark location." );
			descriptions.add( GO_TO_BOOKMARK_ROTATION, GO_TO_BOOKMARK_ROTATION_KEYS, "Retrieve a labeled bookmark, set only the orientation." );
		}
	}

	/**
	 * Create BigVolumeViewer actions and install them in the specified
	 * {@link Actions}.
	 * <p>
	 * Note that
	 *
	 * @param actions
	 *            navigation actions are installed here.
	 * @param bvv
	 *            Actions are targeted at this {@link BigVolumeViewerMamut}.
	 */
	public static void install( final Actions actions, final BigVolumeViewerMamut bvv )
	{
		bookmarks( actions, bvv.bookmarkEditor );
		actions.runnableAction( bvv::expandAndFocusCardPanel, EXPAND_CARDS, EXPAND_CARDS_KEYS );
		actions.runnableAction( bvv::collapseCardPanel, COLLAPSE_CARDS, COLLAPSE_CARDS_KEYS );
	}
}
