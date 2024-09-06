package org.mastodon.views.bvv;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.scijava.Context;
import org.scijava.plugin.PluginService;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.io.gui.CommandDescriptionsBuilder;
import org.scijava.ui.behaviour.util.Actions;

import bdv.cache.CacheControl;
import bdv.tools.PreferencesDialog;
import bdv.tools.bookmarks.Bookmarks;
import bdv.tools.bookmarks.BookmarksEditor;
import bdv.tools.brightness.BrightnessDialog;
import bdv.tools.brightness.ConverterSetup;
import bdv.tools.brightness.MinMaxGroup;
import bdv.tools.brightness.SetupAssignments;
import bdv.tools.transformation.ManualTransformation;
import bdv.tools.transformation.ManualTransformationEditor;
import bdv.ui.appearance.AppearanceManager;
import bdv.ui.appearance.AppearanceSettingsPage;
import bdv.ui.keymap.Keymap;
import bdv.ui.keymap.KeymapManager;
import bdv.ui.keymap.KeymapSettingsPage;
import bdv.viewer.ConverterSetups;
import bdv.viewer.NavigationActions;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerState;
import bvv.core.BigVolumeViewer;
import bvv.core.KeyConfigContexts;
import bvv.core.KeyConfigScopes;
import bvv.core.VolumeViewerOptions;
import bvv.core.VolumeViewerPanel;
import dev.dirs.ProjectDirectories;

/**
 * Copied from {@link BigVolumeViewer}.
 */
public class BigVolumeViewerMamut
{
	public static String configDir = ProjectDirectories.from( "sc", "fiji", "bigvolumeviewer" ).configDir;

	// ... BDV ...
	private final VolumeViewerFrameMamut viewerFrame;
	private final VolumeViewerPanel viewer;
	private final ManualTransformation manualTransformation;
	private final Bookmarks bookmarks;
	private final SetupAssignments setupAssignments;
	final BrightnessDialog brightnessDialog;

	private final KeymapManager keymapManager;
	private final AppearanceManager appearanceManager;
	final PreferencesDialog preferencesDialog;
	final ManualTransformationEditor manualTransformationEditor;
	final BookmarksEditor bookmarkEditor;

	private final JFileChooser fileChooser;
	private File proposedSettingsFile;

	/**
	 *
	 * @param converterSetups
	 *            list of {@link ConverterSetup} that control min/max and color
	 *            of sources.
	 * @param sources
	 *            the {@link SourceAndConverter sources} to display.
	 * @param numTimepoints
	 *            number of available timepoints.
	 * @param cacheControl
	 *            handle to cache. This is used to control io timing.
	 * @param windowTitle
	 *            title of the viewer window.
	 * @param options
	 *            optional parameters. See {@link VolumeViewerOptions}.
	 */
	public BigVolumeViewerMamut(
			final List< ConverterSetup > converterSetups,
			final List< SourceAndConverter< ? > > sources,
			final int numTimepoints,
			final CacheControl cacheControl,
			final String windowTitle,
			final VolumeViewerOptions options )
	{
		final KeymapManager optionsKeymapManager = options.values.getKeymapManager();
		final AppearanceManager optionsAppearanceManager = options.values.getAppearanceManager();
		keymapManager = optionsKeymapManager != null ? optionsKeymapManager : createDefaultKeymapManager();
		appearanceManager = optionsAppearanceManager != null ? optionsAppearanceManager : new AppearanceManager( configDir );

		InputTriggerConfig inputTriggerConfig = options.values.getInputTriggerConfig();
		final Keymap keymap = this.keymapManager.getForwardSelectedKeymap();
		if ( inputTriggerConfig == null )
			inputTriggerConfig = keymap.getConfig();

		viewerFrame = new VolumeViewerFrameMamut( sources, numTimepoints, cacheControl, options.inputTriggerConfig( inputTriggerConfig ) );
		if ( windowTitle != null )
			viewerFrame.setTitle( windowTitle );
		viewer = viewerFrame.getViewerPanel();

		manualTransformation = new ManualTransformation( viewer );
		manualTransformationEditor = new ManualTransformationEditor( viewer, viewerFrame.getKeybindings() );

		bookmarks = new Bookmarks();
		bookmarkEditor = new BookmarksEditor( viewer, viewerFrame.getKeybindings(), bookmarks );

		final ConverterSetups setups = viewerFrame.getConverterSetups();
		if ( converterSetups.size() != sources.size() )
			System.err.println( "WARNING! Constructing BigDataViewer, with converterSetups.size() that is not the same as sources.size()." );
		final int numSetups = Math.min( converterSetups.size(), sources.size() );
		for ( int i = 0; i < numSetups; ++i )
		{
			final SourceAndConverter< ? > source = sources.get( i );
			final ConverterSetup setup = converterSetups.get( i );
			if ( setup != null )
				setups.put( source, setup );
		}

		setupAssignments = new SetupAssignments( new ArrayList<>( converterSetups ), 0, 65535 );
		if ( setupAssignments.getMinMaxGroups().size() > 0 )
		{
			final MinMaxGroup group = setupAssignments.getMinMaxGroups().get( 0 );
			for ( final ConverterSetup setup : setupAssignments.getConverterSetups() )
				setupAssignments.moveSetupToGroup( setup, group );
		}

		brightnessDialog = new BrightnessDialog( viewerFrame, setupAssignments );

		fileChooser = new JFileChooser();
		fileChooser.setFileFilter( new FileFilter()
		{
			@Override
			public String getDescription()
			{
				return "xml files";
			}

			@Override
			public boolean accept( final File f )
			{
				if ( f.isDirectory() )
					return true;
				if ( f.isFile() )
				{
					final String s = f.getName();
					final int i = s.lastIndexOf( '.' );
					if ( i > 0 && i < s.length() - 1 )
					{
						final String ext = s.substring( i + 1 ).toLowerCase();
						return ext.equals( "xml" );
					}
				}
				return false;
			}
		} );

		preferencesDialog = new PreferencesDialog( viewerFrame, keymap, new String[] { KeyConfigContexts.BIGVOLUMEVIEWER } );
		preferencesDialog.addPage( new AppearanceSettingsPage( "Appearance", appearanceManager ) );
		preferencesDialog.addPage( new KeymapSettingsPage( "Keymap", this.keymapManager, this.keymapManager.getCommandDescriptions() ) );
		appearanceManager.appearance().updateListeners().add( viewerFrame::repaint );
		appearanceManager.addLafComponent( fileChooser );
		SwingUtilities.invokeLater(() -> appearanceManager.updateLookAndFeel());

		final Actions navActions = new Actions( inputTriggerConfig, KeyConfigContexts.BIGVOLUMEVIEWER, "navigation" );
		navActions.install( viewerFrame.getKeybindings(), "navigation" );
		NavigationActions.install( navActions, viewer, false );

		final Actions bvvActions = new Actions( inputTriggerConfig, KeyConfigContexts.BIGVOLUMEVIEWER );
		bvvActions.install( viewerFrame.getKeybindings(), "bdv" );

		BigVolumeViewerMamutActions.install( bvvActions, this );

		keymap.updateListeners().add( () -> {
			navActions.updateKeyConfig( keymap.getConfig() );
			bvvActions.updateKeyConfig( keymap.getConfig() );
//			viewerFrame.getTransformBehaviours().updateKeyConfig( keymap.getConfig() );
		} );
	}

	private static KeymapManager createDefaultKeymapManager()
	{
		final KeymapManager manager = new KeymapManager( configDir );
		final CommandDescriptionsBuilder builder = new CommandDescriptionsBuilder();
		final Context context = new Context( PluginService.class );
		context.inject( builder );
		builder.discoverProviders( KeyConfigScopes.BIGVOLUMEVIEWER );
		context.dispose();
		manager.setCommandDescriptions( builder.build() );
		return manager;
	}

	public VolumeViewerPanel getViewer()
	{
		return viewer;
	}

	public VolumeViewerFrameMamut getViewerFrame()
	{
		return viewerFrame;
	}

	public ConverterSetups getConverterSetups()
	{
		return viewerFrame.getConverterSetups();
	}

	/**
	 * @deprecated Instead {@code getViewer().state()} returns the {@link ViewerState} that can be modified directly.
	 */
	@Deprecated
	public SetupAssignments getSetupAssignments()
	{
		return setupAssignments;
	}

	public ManualTransformationEditor getManualTransformEditor()
	{
		return manualTransformationEditor;
	}

	public KeymapManager getKeymapManager()
	{
		return keymapManager;
	}

	public AppearanceManager getAppearanceManager()
	{
		return appearanceManager;
	}

	// -------------------------------------------------------------------------------------------------------
	// BDV ViewerPanel equivalents

	public void loadSettings()
	{
		fileChooser.setSelectedFile( proposedSettingsFile );
		final int returnVal = fileChooser.showOpenDialog( null );
		if ( returnVal == JFileChooser.APPROVE_OPTION )
		{
			proposedSettingsFile = fileChooser.getSelectedFile();
			try
			{
				loadSettings( proposedSettingsFile.getCanonicalPath() );
			}
			catch ( final Exception e )
			{
				e.printStackTrace();
			}
		}
	}

	public void loadSettings( final String xmlFilename ) throws IOException, JDOMException
	{
		final SAXBuilder sax = new SAXBuilder();
		final Document doc = sax.build( xmlFilename );
		final Element root = doc.getRootElement();
		viewer.stateFromXml( root );
		setupAssignments.restoreFromXml( root );
		manualTransformation.restoreFromXml( root );
		bookmarks.restoreFromXml( root );
		viewer.requestRepaint();
	}

	public boolean tryLoadSettings( final String xmlFilename )
	{
		proposedSettingsFile = null;
		if ( xmlFilename.endsWith( ".xml" ) )
		{
			final String settings = xmlFilename.substring( 0, xmlFilename.length() - ".xml".length() ) + ".settings" + ".xml";
			proposedSettingsFile = new File( settings );
			if ( proposedSettingsFile.isFile() )
			{
				try
				{
					loadSettings( settings );
					return true;
				}
				catch ( final Exception e )
				{
					e.printStackTrace();
				}
			}
		}
		return false;
	}

	public void saveSettings()
	{
		fileChooser.setSelectedFile( proposedSettingsFile );
		final int returnVal = fileChooser.showSaveDialog( null );
		if ( returnVal == JFileChooser.APPROVE_OPTION )
		{
			proposedSettingsFile = fileChooser.getSelectedFile();
			try
			{
				saveSettings( proposedSettingsFile.getCanonicalPath() );
			}
			catch ( final IOException e )
			{
				e.printStackTrace();
			}
		}
	}

	public void saveSettings( final String xmlFilename ) throws IOException
	{
		final Element root = new Element( "Settings" );
		root.addContent( viewer.stateToXml() );
		root.addContent( setupAssignments.toXml() );
		root.addContent( manualTransformation.toXml() );
		root.addContent( bookmarks.toXml() );
		final Document doc = new Document( root );
		final XMLOutputter xout = new XMLOutputter( Format.getPrettyFormat() );
		xout.output( doc, new FileWriter( xmlFilename ) );
	}

	public void expandAndFocusCardPanel()
	{
		viewerFrame.getSplitPanel().setCollapsed( false );
		viewerFrame.getSplitPanel().getRightComponent().requestFocusInWindow();
	}

	public void collapseCardPanel()
	{
		viewerFrame.getSplitPanel().setCollapsed( true );
		viewer.requestFocusInWindow();
	}
}
