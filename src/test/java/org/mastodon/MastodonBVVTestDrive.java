package org.mastodon;

import org.mastodon.mamut.launcher.MastodonLauncherCommand;
import org.scijava.Context;

public class MastodonBVVTestDrive
{

	public static void main( final String[] args )
	{
		final MastodonLauncherCommand launcher = new MastodonLauncherCommand();
		try (Context context = new Context())
		{
			context.inject( launcher );
			launcher.run();
		}
	}
}
