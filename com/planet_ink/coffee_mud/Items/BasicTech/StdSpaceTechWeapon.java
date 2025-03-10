package com.planet_ink.coffee_mud.Items.BasicTech;
import com.planet_ink.coffee_mud.Items.Basic.StdItem;
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
   Copyright 2019-2021 Bo Zimmerman

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
public class StdSpaceTechWeapon extends StdSpaceTech implements SpaceObject, Weapon
{
	@Override
	public String ID()
	{
		return "StdSpaceTechWeapon";
	}

	protected int weaponType = Weapon.TYPE_BASHING;
	protected int weaponClass = Weapon.CLASS_BLUNT;

	public StdSpaceTechWeapon()
	{
		super();
		setName("a tech weapon in space");
		setDisplayText("a tech weapon is floating in space");
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(!super.tick(ticking, tickID))
			return false;
		if((ticking == this) && (tickID == Tickable.TICKID_BEAMWEAPON))
		{
			this.destroy();
			return false;
		}
		return true;
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost,msg);

		switch(msg.targetMinor())
		{
		case CMMsg.TYP_DAMAGE: // kinetic damage taken to the body by a weapon
		case CMMsg.TYP_COLLISION:
		{
			if((msg.tool()==this) || (msg.target()==this))
			{
				if (!amDestroyed())
				{
					this.destroy();
				}
			}
			break;
		}
		default:
			break;
		}
	}

	@Override
	public int weaponDamageType()
	{
		return weaponType;
	}

	@Override
	public void setWeaponDamageType(final int newType)
	{
		this.weaponType=newType;
	}

	@Override
	public int weaponClassification()
	{
		return weaponClass;
	}

	@Override
	public void setWeaponClassification(final int newClassification)
	{
		this.weaponClass = newClassification;
	}

	@Override
	public void setRanges(final int min, final int max)
	{
	}

	@Override
	public int[] getRanges()
	{
		return new int[] { minRange(), maxRange() };
	}

	@Override
	public String missString()
	{
		return CMLib.combat().standardMissString(weaponType,weaponClass,name(),true);
	}

	@Override
	public String hitString(final int damageAmount)
	{
		return CMLib.combat().standardHitString(weaponType, weaponClass,damageAmount,name());
	}

}
