package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Races.interfaces.*;

import java.util.*;

/*
   Copyright 2004-2021 Bo Zimmerman

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

	   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
public class Beacon extends StdCommand
{
	public Beacon()
	{
	}

	private final String[] access=I(new String[]{"BEACON"});
	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		commands.remove(0);
		if(commands.size()==0)
		{
			if(mob.getStartRoom()==mob.location())
				mob.tell(L("This is already your beacon."));
			else
			{
				mob.setStartRoom(mob.location());
				mob.tell(L("You have modified your beacon."));
			}
		}
		else
		{
			final String name=CMParms.combine(commands,0);
			MOB M=CMLib.sessions().findPlayerOnline(name,true);
			if(M==null)
				M=CMLib.sessions().findPlayerOnline(name,false);
			if(M==null)
			{
				BoardableShip bI=CMLib.map().getShip(name);
				if(bI==null)
					bI=CMLib.map().findShip(name, true);
				if(bI==null)
					bI=CMLib.map().findShip(name, false);
				if(bI==null)
				{
					mob.tell(L("There are no people or ships online called '@x1'!",name));
					return false;
				}
				final Room biR=CMLib.map().roomLocation(bI);
				if(biR==null)
				{
					mob.tell(L("@x1 is nowhere that can be beaconed to.",bI.name()));
					return false;
				}
				if(!CMSecurity.isAllowed(mob,biR,CMSecurity.SecFlag.BEACON))
				{
					mob.tell(L("You cannot beacon @x1 there.",biR.name()));
					return false;
				}
				if(bI.getHomePortID().equalsIgnoreCase(CMLib.map().getExtendedRoomID(biR)))
				{
					mob.tell(L("@x1 is already at their home port.",bI.name()));
					return false;
				}
				bI.setHomePortID(CMLib.map().getExtendedRoomID(biR));
				mob.tell(L("You have modified @x1's beacon.",bI.name()));
			}
			else
			{
				if(M.getStartRoom()==M.location())
				{
					mob.tell(L("@x1 is already at their beacon.",M.name(mob)));
					return false;
				}
				if(!CMSecurity.isAllowed(mob,M.location(),CMSecurity.SecFlag.BEACON))
				{
					mob.tell(L("You cannot beacon @x1 there.",M.name(mob)));
					return false;
				}
				M.setStartRoom(M.location());
				mob.tell(L("You have modified @x1's beacon.",M.name(mob)));
			}
		}
		return false;
	}

	@Override
	public boolean canBeOrdered()
	{
		return true;
	}

	@Override
	public boolean securityCheck(final MOB mob)
	{
		return CMSecurity.isAllowed(mob,mob.location(),CMSecurity.SecFlag.BEACON);
	}

}
