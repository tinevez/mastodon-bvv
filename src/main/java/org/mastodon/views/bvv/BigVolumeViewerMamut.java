package org.mastodon.views.bvv;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.mastodon.grouping.GroupHandle;
import org.mastodon.views.bdv.SharedBigDataViewerData;

import bdv.cache.CacheControl;
import bdv.tools.bookmarks.Bookmarks;
import bdv.tools.bookmarks.BookmarksEditor;
import bdv.tools.brightness.ConverterSetup;
import bdv.viewer.ConverterSetups;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions.Values;
import bvv.core.BigVolumeViewer;
import bvv.core.VolumeViewerOptions;
import bvv.core.VolumeViewerPanel;
import dev.dirs.ProjectDirectories;

/**
 * Copied from {@link BigVolumeViewer}. Adapted so that the frame returned can
 * be inserted into Mastodon frame hierarchy.
 */
public class BigVolumeViewerMamut
{
	public static String configDir = ProjectDirectories.from( "sc", "fiji", "bigvolumeviewer" ).configDir;

	// ... BDV ...
	private final VolumeViewerFrameMamut viewerFrame;
	private final VolumeViewerPanel viewer;
	private final Bookmarks bookmarks;

	final BookmarksEditor bookmarkEditor;

	private final JFileChooser fileChooser;

	private final SharedBigDataViewerData bdvData;

	public BigVolumeViewerMamut(
			final SharedBigDataViewerData bdvData,
			final String windowTitle,
			final GroupHandle groupHandle )
	{
		this.bdvData = bdvData;
		final ArrayList< SourceAndConverter< ? > > sources = bdvData.getSources();
		final int numTimepoints = bdvData.getNumTimepoints();
		final CacheControl cacheControl = bdvData.getCache();
		final List< ConverterSetup > converterSetups = bdvData.getConverterSetups().getConverterSetups( sources );
		final VolumeViewerOptions options = getOptions( bdvData.getOptions().values );

		viewerFrame = new VolumeViewerFrameMamut(
				sources,
				numTimepoints,
				cacheControl,
				groupHandle,
				options );

		if ( windowTitle != null )
			viewerFrame.setTitle( windowTitle );
		viewer = viewerFrame.getViewerPanel();

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
//
//		setupAssignments = new SetupAssignments( new ArrayList<>( converterSetups ), 0, 65535 );
//		if ( setupAssignments.getMinMaxGroups().size() > 0 )
//		{
//			final MinMaxGroup group = setupAssignments.getMinMaxGroups().get( 0 );
//			for ( final ConverterSetup setup : setupAssignments.getConverterSetups() )
//				setupAssignments.moveSetupToGroup( setup, group );
//		}

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

	protected void trySaveSettings()
	{
		fileChooser.setSelectedFile( bdvData.getProposedSettingsFile() );
		final int returnVal = fileChooser.showSaveDialog( null );
		if ( returnVal == JFileChooser.APPROVE_OPTION )
		{
			final File file = fileChooser.getSelectedFile();
			bdvData.setProposedSettingsFile( file );
			try
			{
				saveSettings( file.getCanonicalPath() );
			}
			catch ( final IOException e )
			{
				e.printStackTrace();
			}
		}
	}

	protected void tryLoadSettings()
	{
		fileChooser.setSelectedFile( bdvData.getProposedSettingsFile() );
		final int returnVal = fileChooser.showOpenDialog( null );
		if ( returnVal == JFileChooser.APPROVE_OPTION )
		{
			final File file = fileChooser.getSelectedFile();
			bdvData.setProposedSettingsFile( file );
			try
			{
				loadSettings( file.getCanonicalPath() );
				viewer.repaint();
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
		if ( viewer != null )
			viewer.stateFromXml( root );
		bdvData.restoreFromXmlSetupAssignments( root );
		bdvData.getManualTransformation().restoreFromXml( root );
		bookmarks.restoreFromXml( root );
	}

	public void saveSettings( final String xmlFilename ) throws IOException
	{
		final Element root = new Element( "Settings" );
		if ( viewer != null )
			root.addContent( viewer.stateToXml() );
		root.addContent( bdvData.toXmlSetupAssignments() );
		root.addContent( bdvData.getManualTransformation().toXml() );
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

	protected VolumeViewerOptions getOptions( final Values values )
	{
		final int windowWidth = 640;
		final int windowHeight = 480;
		final int renderWidth = 512;
		final int renderHeight = 512;

//		final int windowWidth = 1280;
//		final int windowHeight = 960;
//		final int renderWidth = 1280;
//		final int renderHeight = 960;

//		final int renderWidth = 3840;
//		final int renderHeight = 1600;
		final int ditherWidth = 8;
		final int numDitherSamples = 8;
		final int cacheBlockSize = 64;
		final int maxCacheSizeInMB = 4000;
		final double dCam = 2000;
		final double dClip = 1000;

		final VolumeViewerOptions options = VolumeViewerOptions.options()
				.width( windowWidth )
				.height( windowHeight )
				.renderWidth( renderWidth )
				.renderHeight( renderHeight )
				.ditherWidth( ditherWidth )
				.numDitherSamples( numDitherSamples )
				.cacheBlockSize( cacheBlockSize )
				.maxCacheSizeInMB( maxCacheSizeInMB )
				.dCam( dCam )
				.dClip( dClip )
				.appearanceManager( values.getAppearanceManager() )
				.keymapManager( values.getKeymapManager() )
				.inputTriggerConfig( values.getInputTriggerConfig() )
				.msgOverlay( values.getMsgOverlay() )
				.shareKeyPressedEvents( values.getKeyPressedManager() )
				.transformEventHandlerFactory( values.getTransformEventHandlerFactory() )
				.numSourceGroups( values.getNumSourceGroups() );
		return options;
	}
}
