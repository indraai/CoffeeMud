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
public class Pull extends Go
{
	public Pull()
	{
	}

	private final String[]	access	= I(new String[] { "PULL", "DRAG" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		final Vector<String> origCmds=new XVector<String>(commands);
		Physical pullThis=null;
		String dir="";
		int dirCode=-1;
		Environmental E=null;
		if(commands.size()>1)
		{
			dirCode=CMLib.directions().getGoodDirectionCode(commands.get(commands.size()-1));
			if(dirCode>=0)
			{
				if((mob.location().getRoomInDir(dirCode)==null)
				||(mob.location().getExitInDir(dirCode)==null)
				||(!mob.location().getExitInDir(dirCode).isOpen()))
				{
					CMLib.commands().postCommandFail(mob,origCmds,L("You can't pull anything that way."));
					return false;
				}
				E=mob.location().getRoomInDir(dirCode);
				dir=" "+(((mob.location() instanceof BoardableShip)||(mob.location().getArea() instanceof BoardableShip))?
						CMLib.directions().getShipDirectionName(dirCode):CMLib.directions().getDirectionName(dirCode));
				commands.remove(commands.size()-1);
			}
		}
		if(dir.length()==0)
		{
			dirCode=CMLib.directions().getGoodDirectionCode(commands.get(commands.size()-1));
			if(dirCode>=0)
				pullThis=mob.location().getExitInDir(dirCode);
		}
		final String itemName=CMParms.combine(commands,1);
		if(pullThis==null)
			pullThis=mob.location().fetchFromRoomFavorItems(null,itemName);
		if(pullThis==null)
			pullThis=mob.location().fetchFromMOBRoomFavorsItems(mob,null,itemName,Wearable.FILTER_ANY);
		if((pullThis==null)||(!CMLib.flags().canBeSeenBy(pullThis,mob)))
		{
			CMLib.commands().postCommandFail(mob,origCmds,L("You don't see '@x1' here.",itemName));
			return false;
		}
		final CMMsg msg=CMClass.getMsg(mob,pullThis,E,CMMsg.MSG_PULL,L("<S-NAME> pull(s) <T-NAME>@x1.",dir));
		if(mob.location().okMessage(mob,msg))
		{
			mob.location().send(mob,msg);
			if((dir.length()>0)&&(msg.tool() instanceof Room))
			{
				final Room R=(Room)msg.tool();
				dirCode=CMLib.tracking().findRoomDir(mob,R);
				if((dirCode>=0)&&(CMLib.tracking().walk(mob,dirCode,false,false,false,false)))
				{
					int expense = Math.round(CMath.sqrt(pullThis.phyStats().weight()));
					if(expense < CMProps.getIntVar(CMProps.Int.RUNCOST))
						expense = CMProps.getIntVar(CMProps.Int.RUNCOST);
					for(int i=0;i<expense;i++)
						CMLib.combat().expendEnergy(mob,true);
					if(pullThis instanceof Item)
						R.moveItemTo((Item)pullThis,ItemPossessor.Expire.Player_Drop,ItemPossessor.Move.Followers);
					else
					if(pullThis instanceof MOB)
						CMLib.tracking().walk((MOB)pullThis,dirCode,((MOB)pullThis).isInCombat(),false,true,true);
					final int movesRequired = pullThis.phyStats().movesReqToPull() - (mob.maxCarry() / 3);
					if(movesRequired > 0)
						mob.curState().adjMovement(-movesRequired, mob.maxState());
				}
			}
		}
		return false;
	}

	@Override
	public double combatActionsCost(final MOB mob, final List<String> cmds)
	{
		return CMProps.getCommandCombatActionCost(ID());
	}

	@Override
	public double actionsCost(final MOB mob, final List<String> cmds)
	{
		return CMProps.getCommandActionCost(ID());
	}

	@Override
	public boolean canBeOrdered()
	{
		return true;
	}

}
