package org.mastodon.views.bvv;

import static org.mastodon.app.MastodonIcons.BVV_VIEW_ICON;
import static org.mastodon.mamut.MamutMenuBuilder.colorMenu;
import static org.mastodon.mamut.MamutMenuBuilder.colorbarMenu;
import static org.mastodon.mamut.MamutMenuBuilder.editMenu;
import static org.mastodon.mamut.MamutMenuBuilder.fileMenu;
import static org.mastodon.mamut.MamutMenuBuilder.viewMenu;

import java.util.function.Consumer;

import javax.swing.ActionMap;
import javax.swing.JFrame;
import javax.swing.WindowConstants;

import org.mastodon.adapter.RefBimap;
import org.mastodon.app.ui.MastodonFrameViewActions;
import org.mastodon.app.ui.ViewMenu;
import org.mastodon.app.ui.ViewMenuBuilder.JMenuHandle;
import org.mastodon.graph.GraphListener;
import org.mastodon.mamut.MainWindow;
import org.mastodon.mamut.MamutMenuBuilder;
import org.mastodon.mamut.ProjectModel;
import org.mastodon.mamut.io.ProjectLoader;
import org.mastodon.mamut.model.Link;
import org.mastodon.mamut.model.Model;
import org.mastodon.mamut.model.ModelGraph;
import org.mastodon.mamut.model.ModelOverlayProperties;
import org.mastodon.mamut.model.Spot;
import org.mastodon.mamut.model.branch.BranchLink;
import org.mastodon.mamut.model.branch.BranchSpot;
import org.mastodon.mamut.views.MamutView;
import org.mastodon.model.AutoNavigateFocusModel;
import org.mastodon.model.FocusModel;
import org.mastodon.model.HighlightModel;
import org.mastodon.model.SelectionModel;
import org.mastodon.ui.FocusActions;
import org.mastodon.ui.coloring.ColorBarOverlay;
import org.mastodon.ui.coloring.ColoringModelMain;
import org.mastodon.ui.coloring.GraphColorGenerator;
import org.mastodon.ui.coloring.GraphColorGeneratorAdapter;
import org.mastodon.ui.keymap.KeyConfigContexts;
import org.mastodon.views.bdv.SharedBigDataViewerData;
import org.mastodon.views.bdv.overlay.OverlayNavigation;
import org.mastodon.views.bdv.overlay.RenderSettings;
import org.mastodon.views.bdv.overlay.RenderSettings.UpdateListener;
import org.mastodon.views.bdv.overlay.ui.RenderSettingsManager;
import org.mastodon.views.bdv.overlay.wrap.OverlayEdgeWrapper;
import org.mastodon.views.bdv.overlay.wrap.OverlayGraphWrapper;
import org.mastodon.views.bdv.overlay.wrap.OverlayVertexWrapper;
import org.mastodon.views.bvv.scene.OverlaySceneRenderer;
import org.scijava.Context;
import org.scijava.thread.ThreadService;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Actions;

import bdv.tools.InitializeViewerState;
import bdv.viewer.NavigationActions;
import bvv.core.VolumeViewerPanel;
import bvv.core.render.VolumeRenderer.RepaintType;
import net.imglib2.realtransform.AffineTransform3D;

public class MamutViewBvv extends MamutView< OverlayGraphWrapper< Spot, Link >, OverlayVertexWrapper< Spot, Link >, OverlayEdgeWrapper< Spot, Link > >
{

	private static int bvvName = 1;

	private final ColoringModelMain< Spot, Link, BranchSpot, BranchLink > coloringModel;

	private final ColorBarOverlay colorBarOverlay;

	public MamutViewBvv(
			final ProjectModel projectModel,
			final OverlayGraphWrapper< Spot, Link > viewGraph,
			final String[] keyConfigContexts )
	{
		super( projectModel, createViewGraph( projectModel ), keyConfigContexts );
		final SharedBigDataViewerData bdvData = projectModel.getSharedBdvData();

		final String windowTitle = "BigVolumeViewer " + ( bvvName++ );
		final BigVolumeViewerMamut bvv = new BigVolumeViewerMamut(
				bdvData,
				windowTitle,
				groupHandle );

		// Coloring for vertices and edges.
		final GraphColorGeneratorAdapter< Spot, Link, OverlayVertexWrapper< Spot, Link >, OverlayEdgeWrapper< Spot, Link > > coloring = new GraphColorGeneratorAdapter<>( viewGraph.getVertexMap(), viewGraph.getEdgeMap() );

		// View frame.
		final VolumeViewerFrameMamut frame = bvv.getViewerFrame();
		setFrame( frame );
		frame.setIconImages( BVV_VIEW_ICON );

		// Actions related to the frame.
		MastodonFrameViewActions.install( viewActions, this );
		BigVolumeViewerActionsMamut.install( viewActions, bvv );

		/*
		 * We have to build the coloring menu handles now. But the other actions
		 * need to be included in the menus later, after they have been
		 * installed (otherwise they won't be active). To keep the future menu
		 * order, we build an empty menu but already with all sub-menus in
		 * order.
		 */
		final JMenuHandle menuHandle = new JMenuHandle();
		final JMenuHandle tagSetMenuHandle = new JMenuHandle();
		final JMenuHandle colorbarMenuHandle = new JMenuHandle();
		final ViewMenu menu = new ViewMenu( this );
		final ActionMap actionMap = frame.getKeybindings().getConcatenatedActionMap();
		MamutMenuBuilder.build( menu, actionMap,
				fileMenu(),
				viewMenu(
						colorMenu( menuHandle ),
						colorbarMenu( colorbarMenuHandle ) ),
				editMenu() );

		// The content panel.
		final VolumeViewerPanel viewer = bvv.getViewer();

		// This view's render settings
		final RenderSettingsManager renderSettingsManager = appModel.getWindowManager().getManager( RenderSettingsManager.class );
		final RenderSettings renderSettings = renderSettingsManager.getForwardDefaultStyle();

		// The spot & link overlay.
		final OverlaySceneRenderer< OverlayVertexWrapper< Spot, Link >, OverlayEdgeWrapper< Spot, Link > > tracksOverlay = createRenderer(
				viewGraph,
				highlightModel,
				focusModel,
				selectionModel,
				coloring,
				renderSettings );
		viewer.setRenderScene( tracksOverlay );

		// Listeners that will update the scene.

		// Update colors when the color mode or the render settings change.
		final Runnable colorUpdater = () -> {
			tracksOverlay.updateColors();
			viewer.requestRepaint( RepaintType.SCENE );
		};

		// Update position of one vertex when user moves it.
		final RefBimap< Spot, OverlayVertexWrapper< Spot, Link > > vertexMap = viewGraph.getVertexMap();
		final Consumer< Spot > positionUpdater = ( s ) -> {
			final OverlayVertexWrapper< Spot, Link > oref = viewGraph.vertexRef();
			final OverlayVertexWrapper< Spot, Link > v = vertexMap.getRight( s, oref );
			tracksOverlay.updatePosition( v );
			viewer.requestRepaint( RepaintType.FULL );
			viewGraph.releaseRef( oref );
		};

		// Update shape of one vertex when user edit it.
		final Consumer< Spot > shapeUpdater = ( s ) -> {
			final OverlayVertexWrapper< Spot, Link > oref = viewGraph.vertexRef();
			final OverlayVertexWrapper< Spot, Link > v = vertexMap.getRight( s, oref );
			tracksOverlay.updateShape( v );
			viewer.requestRepaint( RepaintType.FULL );
			viewGraph.releaseRef( oref );
		};

		// Register color menus and model.
		coloringModel = registerColoring( coloring, menuHandle, colorUpdater );
		colorBarOverlay = new ColorBarOverlay( coloringModel, () -> viewer.getBackground() );
		registerColorbarOverlay( colorBarOverlay, colorbarMenuHandle, () -> viewer.requestRepaint( RepaintType.SCENE ) );

		// Notifies when the render settings change.
		final UpdateListener updateListener = () -> colorUpdater.run();
		renderSettings.updateListeners().add( updateListener );
		onClose( () -> renderSettings.updateListeners().remove( updateListener ) );

		// Notify if models update.
		final Model model = appModel.getModel();
		final ModelGraph modelGraph = model.getGraph();
		modelGraph.addGraphListener( new MyGraphListener( tracksOverlay, () -> viewer.requestRepaint( RepaintType.FULL ) ) );
		modelGraph.addVertexPositionListener( spot -> positionUpdater.accept( spot ) );
		modelGraph.addVertexCovarianceListener( spot -> shapeUpdater.accept( spot ) );
		selectionModel.listeners().add( () -> colorUpdater.run() );

		NavigationActions.install( viewActions, viewer, bdvData.is2D() );
		viewer.getTransformEventHandler().install( viewBehaviours );

		// Navigate based on view group.
		final OverlayNavigation< OverlayVertexWrapper< Spot, Link >, OverlayEdgeWrapper< Spot, Link > > overlayNavigation = new OverlayNavigation<>( viewer, viewGraph );
		navigationHandler.listeners().add( overlayNavigation );

		// Navigate based on focus.
		final AutoNavigateFocusModel< OverlayVertexWrapper< Spot, Link >, OverlayEdgeWrapper< Spot, Link > > navigateFocusModel =
				new AutoNavigateFocusModel<>( focusModel, navigationHandler );
		FocusActions.install( viewActions, viewGraph, viewGraph.getLock(), navigateFocusModel, selectionModel );

		final int windowWidth = frame.getWidth();
		final int windowHeight = frame.getHeight();
		final AffineTransform3D resetTransform = InitializeViewerState.initTransform( windowWidth, windowHeight, false, viewer.state() );
		viewer.state().setViewerTransform( resetTransform );
		final Actions actions = new Actions( new InputTriggerConfig() );
		actions.install( frame.getKeybindings(), "additional" );
		actions.runnableAction( () -> {
			viewer.state().setViewerTransform( resetTransform );
			viewer.showMessage( "reset view" );
		}, "reset transform", "R" );

		onClose( () -> viewer.stop() );
		onClose( () -> tracksOverlay.stop() );

		frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
		frame.setVisible( true );
	}

	private static OverlayGraphWrapper< Spot, Link > createViewGraph( final ProjectModel appModel )
	{
		return new OverlayGraphWrapper<>(
				appModel.getModel().getGraph(),
				appModel.getModel().getGraphIdBimap(),
				appModel.getModel().getSpatioTemporalIndex(),
				appModel.getModel().getGraph().getLock(),
				new ModelOverlayProperties( appModel.getModel().getGraph(), appModel.getRadiusStats() ) );
	}

	protected OverlaySceneRenderer< OverlayVertexWrapper< Spot, Link >, OverlayEdgeWrapper< Spot, Link > >
			createRenderer(
					final OverlayGraphWrapper< Spot, Link > viewGraph,
					final HighlightModel< OverlayVertexWrapper< Spot, Link >, OverlayEdgeWrapper< Spot, Link > > highlightModel,
					final FocusModel< OverlayVertexWrapper< Spot, Link > > focusModel,
					final SelectionModel< OverlayVertexWrapper< Spot, Link >, OverlayEdgeWrapper< Spot, Link > > selectionModel,
					final GraphColorGenerator< OverlayVertexWrapper< Spot, Link >, OverlayEdgeWrapper< Spot, Link > > coloring,
					final RenderSettings renderSettings )
	{
		return new OverlaySceneRenderer< OverlayVertexWrapper< Spot, Link >, OverlayEdgeWrapper< Spot, Link > >(
				viewGraph,
				highlightModel,
				focusModel,
				selectionModel,
				coloring,
				renderSettings );
	}

	/**
	 * Forwards {@link GraphListener} events of the model graph to the track
	 * overlay.
	 */
	private static class MyGraphListener implements GraphListener< Spot, Link >
	{

		private final OverlaySceneRenderer< OverlayVertexWrapper< Spot, Link >, OverlayEdgeWrapper< Spot, Link > > tracksOverlay;

		private final Runnable refresh;

		public MyGraphListener( final OverlaySceneRenderer< OverlayVertexWrapper< Spot, Link >, OverlayEdgeWrapper< Spot, Link > > tracksOverlay,
				final Runnable refresh )
		{
			this.tracksOverlay = tracksOverlay;
			this.refresh = refresh;
		}

		@Override
		public void graphRebuilt()
		{
			tracksOverlay.rebuild();
			refresh.run();
		}

		@Override
		public void vertexAdded( final Spot s )
		{
			tracksOverlay.rebuild( s.getTimepoint() );
			refresh.run();
		}

		@Override
		public void vertexRemoved( final Spot s )
		{
			vertexAdded( s );
		}

		@Override
		public void edgeAdded( final Link l )
		{}

		@Override
		public void edgeRemoved( final Link l )
		{}

	}

	public static void main( final String[] args )
	{

		final String projectPath = "../mastodon/samples/drosophila_crop.mastodon";
//		final String projectPath = "../mastodon/samples/MaMuT_Parhyale_small.mastodon";
//		final String projectPath = "/Users/tinevez/Google Drive/Mastodon/Datasets/Remote/ParhyaleHawaiensis/MaMuT_Parhyale_demo-mamut.mastodon";
//		final String projectPath = "/Users/tinevez/Google Drive/Mastodon/Datasets/Remote/Tribolium/CTC_TRIF_trainingVideo02_jy-tracked.mastodon";

		System.setProperty( "apple.laf.useScreenMenuBar", "true" );
		try (Context context = new Context())
		{
			final ThreadService threadService = context.getService( ThreadService.class );
			threadService.run( () -> {
				try
				{
					final ProjectModel projectModel = ProjectLoader.open( projectPath, context, false, false );
					final MainWindow win = new MainWindow( projectModel );
					win.setVisible( true );
					win.setDefaultCloseOperation( WindowConstants.EXIT_ON_CLOSE );
					new MamutViewBvv(
							projectModel,
							createViewGraph( projectModel ),
							new String[] { KeyConfigContexts.BIGDATAVIEWER } );
				}
				catch ( final Exception e )
				{
					e.printStackTrace();
				}
			} );
		}
	}
}
