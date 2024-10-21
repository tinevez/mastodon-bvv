/*-
 * #%L
 * Mastodon
 * %%
 * Copyright (C) 2014 - 2024 Tobias Pietzsch, Jean-Yves Tinevez
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package org.mastodon.views.bvv.scene;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

import org.scijava.listeners.Listeners;

import bdv.ui.settings.style.Style;
import bvv.core.shadergen.generate.Segment;
import bvv.core.shadergen.generate.SegmentTemplate;

public class BvvRenderSettings implements Style< BvvRenderSettings >
{
	/*
	 * PUBLIC DISPLAY CONFIG DEFAULTS.
	 */

	public static final boolean DEFAULT_USE_ANTI_ALIASING = true;

	public static final boolean DEFAULT_DRAW_SPOTS = true;

	public static final boolean DEFAULT_DRAW_SPOTS_AS_SURFACES = true;

	public static final int DEFAULT_COLOR = Color.GREEN.getRGB();

	public static final int DEFAULT_SELECTION_COLOR = Color.MAGENTA.getRGB();

	public static final double DEFAULT_ALPHA = 0.8;

	public static final ShaderStyle DEFAULT_STYLE = ShaderStyle.CLASSIC;

	public interface UpdateListener
	{
		public void bvvRenderSettingsChanged();
	}

	private final Listeners.List< UpdateListener > updateListeners;

	private BvvRenderSettings()
	{
		updateListeners = new Listeners.SynchronizedList<>();
	}

	/**
	 * Returns a new BVV render settings, copied from this instance.
	 *
	 * @param name
	 *            the name for the copied render settings.
	 * @return a new {@link BvvRenderSettings} instance.
	 */
	@Override
	public BvvRenderSettings copy( final String name )
	{
		final BvvRenderSettings rs = new BvvRenderSettings();
		rs.set( this );
		if ( name != null )
			rs.setName( name );
		return rs;
	}

	@Override
	public BvvRenderSettings copy()
	{
		return copy( null );
	}

	public synchronized void set( final BvvRenderSettings settings )
	{
		name = settings.name;
		useAntialiasing = settings.useAntialiasing;
		drawSpots = settings.drawSpots;
		drawSpotsAsSurfaces = settings.drawSpotsAsSurfaces;
		colorSpot = settings.colorSpot;
		colorSelection = settings.colorSelection;
		transparencyAlpha = settings.transparencyAlpha;
		shaderStyle = settings.shaderStyle;
		notifyListeners();
	}

	private void notifyListeners()
	{
		for ( final UpdateListener l : updateListeners.list )
			l.bvvRenderSettingsChanged();
	}

	public Listeners< UpdateListener > updateListeners()
	{
		return updateListeners;
	}

	/*
	 * DISPLAY SETTINGS FIELDS.
	 */

	/**
	 * The name of this render settings object.
	 */
	private String name;

	/**
	 * Whether to use antialiasing (for drawing everything).
	 */
	private boolean useAntialiasing;

	/**
	 * Whether to draw spots (at all).
	 */
	private boolean drawSpots;

	/**
	 * Whether to draw spots as surface or as points.
	 */
	private boolean drawSpotsAsSurfaces;

	/**
	 * The color used to paint spots in the current time-point.
	 */
	private int colorSpot;

	/**
	 * The color used to paint selected spots.
	 */
	private int colorSelection;

	/**
	 * The transparency alpha value to use when painting spots.
	 */
	private double transparencyAlpha;

	/**
	 * The shaders to use to paint spots.
	 */
	private ShaderStyle shaderStyle;

	/**
	 * Returns the name of this {@link BvvRenderSettings}.
	 *
	 * @return the name.
	 */
	@Override
	public String getName()
	{
		return name;
	}

	/**
	 * Sets the name of this {@link BvvRenderSettings}.
	 *
	 * @param name
	 *            the name to set.
	 */
	@Override
	public synchronized void setName( final String name )
	{
		if ( !Objects.equals( this.name, name ) )
		{
			this.name = name;
			notifyListeners();
		}
	}

	/**
	 * Get the antialiasing setting.
	 *
	 * @return {@code true} if antialiasing is used.
	 */
	public boolean getUseAntialiasing()
	{
		return useAntialiasing;
	}

	/**
	 * Sets whether to use anti-aliasing for drawing.
	 *
	 * @param useAntialiasing
	 *            whether to use use anti-aliasing.
	 */
	public synchronized void setUseAntialiasing( final boolean useAntialiasing )
	{
		if ( this.useAntialiasing != useAntialiasing )
		{
			this.useAntialiasing = useAntialiasing;
			notifyListeners();
		}
	}

	/**
	 * Gets whether to draw spots at all.
	 */
	public boolean getDrawSpots()
	{
		return drawSpots;
	}

	/**
	 * Sets whether to draw spots at all.
	 */
	public synchronized void setDrawSpots( final boolean drawSpots )
	{
		if ( this.drawSpots != drawSpots )
		{
			this.drawSpots = drawSpots;
			notifyListeners();
		}
	}

	/**
	 * Gets whether to draw spots as surfaces. Otherwise they will be draw as
	 * points.
	 */
	public boolean getDrawSpotsAsSurfaces()
	{
		return drawSpotsAsSurfaces;
	}

	/**
	 * Sets whether to draw spots at all.
	 */
	public synchronized void setDrawSpotsAsSurfaces( final boolean drawSpotsAsSurfaces )
	{
		if ( this.drawSpotsAsSurfaces != drawSpotsAsSurfaces )
		{
			this.drawSpotsAsSurfaces = drawSpotsAsSurfaces;
			notifyListeners();
		}
	}

	/**
	 * Gets the transparency alpha value to use when painting spots.
	 * 
	 * @return the transparency alpha value to use when painting spots.
	 */
	public double getTransparencyAlpha()
	{
		return transparencyAlpha;
	}

	/**
	 * Sets the transparency alpha value to use when painting spots.
	 */
	public synchronized void setTransparencyAlpha( final double transparencyAlpha )
	{
		if ( this.transparencyAlpha != transparencyAlpha )
		{
			this.transparencyAlpha = transparencyAlpha;
			notifyListeners();
		}
	}

	/**
	 * Returns the color used to paint spots.
	 * 
	 * @return the color used to paint spots.
	 */
	public int getColorSpot()
	{
		return colorSpot;
	}

	/**
	 * Sets the color used to paint spots.
	 * 
	 * @param colorSpot
	 *            the color used to paint spots.
	 */
	public synchronized void setColorSpot( final int colorSpot )
	{
		if ( this.colorSpot != colorSpot )
		{
			this.colorSpot = colorSpot;
			notifyListeners();
		}
	}

	/**
	 * Returns the color used to paint selected spots.
	 * 
	 * @return the color used to paint selected spots.
	 */
	public int getSelectionColor()
	{
		return colorSelection;
	}

	/**
	 * Sets the color used to paint selected spots.
	 * 
	 * @param colorSelection
	 *            the color used to paint selected spots.
	 */
	public synchronized void setSelectionColor( final int colorSelection )
	{
		if ( this.colorSelection != colorSelection )
		{
			this.colorSelection = colorSelection;
			notifyListeners();
		}
	}

	/**
	 * Returns the shader style used to paint spots.
	 * 
	 * @return the shader style used to paint spots.
	 */
	public ShaderStyle getShaderStyle()
	{
		return shaderStyle;
	}

	/**
	 * Sets the shader style used to paint spots.
	 * 
	 * @param shaderStyle
	 *            the shader style used to paint spots.
	 */
	public synchronized void setShaderStyle( final ShaderStyle shaderStyle )
	{
		if ( this.shaderStyle != shaderStyle )
		{
			this.shaderStyle = shaderStyle;
			notifyListeners();
		}
	}

	/*
	 * DEFAULTS RENDER SETTINGS LIBRARY.
	 */

	private static final BvvRenderSettings df;
	static
	{
		df = new BvvRenderSettings();
		df.useAntialiasing = DEFAULT_USE_ANTI_ALIASING;
		df.drawSpots = DEFAULT_DRAW_SPOTS;
		df.drawSpotsAsSurfaces = DEFAULT_DRAW_SPOTS_AS_SURFACES;
		df.colorSpot = DEFAULT_COLOR;
		df.colorSelection = DEFAULT_SELECTION_COLOR;
		df.shaderStyle = ShaderStyle.CLASSIC;
		df.transparencyAlpha = DEFAULT_ALPHA;
		df.name = "Default";
	}

	private static final BvvRenderSettings POINT_CLOUD;
	static
	{
		POINT_CLOUD = df.copy( "Point cloud" );
		POINT_CLOUD.drawSpotsAsSurfaces = false;
	}

	public static final Collection< BvvRenderSettings > defaults;
	static
	{
		defaults = new ArrayList<>( 4 );
		defaults.add( df );
		defaults.add( POINT_CLOUD );
	}

	public static BvvRenderSettings defaultStyle()
	{
		return df;
	}

	/**
	 * What shader files to use to achieve a given rendering style.
	 */
	public static enum ShaderStyle
	{
		CLASSIC( "Classic", FrameRenderer.class, "vertexShader3D.glsl", "fragmentShader3D.glsl" );

		private final Class< ? > clazz;

		private final String vertexShaderFilename;

		private final String fragmentShaderFilename;

		private final String name;

		private ShaderStyle( final String name, final Class< ? > clazz, final String vertexShaderFilename, final String fragmentShaderFilename )
		{
			this.name = name;
			this.clazz = clazz;
			this.vertexShaderFilename = vertexShaderFilename;
			this.fragmentShaderFilename = fragmentShaderFilename;
		}

		@Override
		public String toString()
		{
			return name;
		}

		public Segment getVertexShaderSegment()
		{
			return new SegmentTemplate( clazz, vertexShaderFilename ).instantiate();
		}

		public Segment getFragmentShaderSegment()
		{
			return new SegmentTemplate( clazz, fragmentShaderFilename ).instantiate();
		}
	}
}
