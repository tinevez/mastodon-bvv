package org.mastodon.views.bvv;

import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import org.mastodon.app.ui.GroupLocksPanel;
import org.mastodon.app.ui.ViewFrame;
import org.mastodon.grouping.GroupHandle;
import org.scijava.ui.behaviour.MouseAndKeyHandler;
import org.scijava.ui.behaviour.util.InputActionBindings;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import bdv.cache.CacheControl;
import bdv.ui.BdvDefaultCards;
import bdv.ui.CardPanel;
import bdv.ui.appearance.AppearanceManager;
import bdv.ui.keymap.KeymapManager;
import bdv.ui.splitpanel.SplitPanel;
import bdv.viewer.ConverterSetups;
import bdv.viewer.SourceAndConverter;
import bvv.core.VolumeViewerFrame;
import bvv.core.VolumeViewerOptions;
import bvv.core.VolumeViewerPanel;

/**
 * A {@link JFrame} containing a {@link VolumeViewerPanel} and associated
 * {@link InputActionBindings}.
 * <p>
 * Copied and adapted from {@link VolumeViewerFrame} so that it extends
 * Mastodon's {@link ViewFrame}.
 *
 * @author Tobias Pietzsch
 */
public class VolumeViewerFrameMamut extends ViewFrame
{
	private final VolumeViewerPanel viewer;

	private final CardPanel cards;

	private final SplitPanel splitPanel;

	private final AppearanceManager appearanceManager;
//
//	public VolumeViewerFrameMamut(
//			final List< SourceAndConverter< ? > > sources,
//			final int numTimepoints,
//			final CacheControl cache,
//			final VolumeViewerOptions optional )
//	{
//		this(
//				sources,
//				numTimepoints,
//				cache,
//				new KeymapManager( BigVolumeViewer.configDir ),
//				new AppearanceManager( BigVolumeViewer.configDir ),
//				optional );
//	}

	/**
	 *
	 * @param sources
	 *            the {@link SourceAndConverter sources} to display.
	 * @param numTimepoints
	 *            number of available timepoints.
	 * @param cacheControl
	 *            handle to cache. This is used to control io timing.
	 * @param optional
	 *            optional parameters. See {@link VolumeViewerOptions}.
	 */
	public VolumeViewerFrameMamut(
			final List< SourceAndConverter< ? > > sources,
			final int numTimepoints,
			final CacheControl cacheControl,
			final KeymapManager keymapManager,
			final AppearanceManager appearanceManager,
			final GroupHandle groupHandle,
			final VolumeViewerOptions optional )
	{
		super( "BigVolumeViewer" );
		this.appearanceManager = appearanceManager;
		viewer = new VolumeViewerPanel( sources, numTimepoints, cacheControl, optional );

		// Main view panel & config card panel.
		cards = new CardPanel();
		BdvDefaultCards.setup( cards, viewer, viewer.getConverterSetups() );
		splitPanel = new SplitPanel( viewer, cards );

		getRootPane().setDoubleBuffered( true );
		add( splitPanel, BorderLayout.CENTER );

		// Settings panel
		final GroupLocksPanel navigationLocksPanel = new GroupLocksPanel( groupHandle );
		settingsPanel.add( navigationLocksPanel );
		settingsPanel.add( Box.createHorizontalGlue() );

		pack();
		setDefaultCloseOperation( WindowConstants.DISPOSE_ON_CLOSE );
		addWindowListener( new WindowAdapter()
		{
			@Override
			public void windowClosing( final WindowEvent e )
			{
				viewer.stop();
			}
		} );

		// TODO: getRootPanel --> viewer ???
		SwingUtilities.replaceUIActionMap( getRootPane(), keybindings.getConcatenatedActionMap() );
		SwingUtilities.replaceUIInputMap( getRootPane(), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, keybindings.getConcatenatedInputMap() );

		final MouseAndKeyHandler mouseAndKeyHandler = new MouseAndKeyHandler();
		mouseAndKeyHandler.setInputMap( triggerbindings.getConcatenatedInputTriggerMap() );
		mouseAndKeyHandler.setBehaviourMap( triggerbindings.getConcatenatedBehaviourMap() );
		mouseAndKeyHandler.setKeypressManager( optional.values.getKeyPressedManager(), viewer.getDisplay().getComponent() );
		viewer.getDisplay().addHandler( mouseAndKeyHandler );
	}

	public VolumeViewerPanel getViewerPanel()
	{
		return viewer;
	}

	public CardPanel getCardPanel()
	{
		return cards;
	}

	public SplitPanel getSplitPanel()
	{
		return splitPanel;
	}

	@Override
	public InputActionBindings getKeybindings()
	{
		return keybindings;
	}

	@Override
	public TriggerBehaviourBindings getTriggerbindings()
	{
		return triggerbindings;
	}

	public ConverterSetups getConverterSetups()
	{
		return viewer.getConverterSetups();
	}
}
