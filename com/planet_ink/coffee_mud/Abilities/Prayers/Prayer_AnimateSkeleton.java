package com.planet_ink.coffee_mud.Abilities.Prayers;
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
   Copyright 2003-2021 Bo Zimmerman

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
public class Prayer_AnimateSkeleton extends Prayer
{
	@Override
	public String ID()
	{
		return "Prayer_AnimateSkeleton";
	}

	private final static String	localizedName	= CMLib.lang().L("Animate Skeleton");

	@Override
	public String name()
	{
		return localizedName;
	}

	@Override
	public int classificationCode()
	{
		return Ability.ACODE_PRAYER | Ability.DOMAIN_DEATHLORE;
	}

	@Override
	public int abstractQuality()
	{
		return Ability.QUALITY_INDIFFERENT;
	}

	@Override
	public int enchantQuality()
	{
		return Ability.QUALITY_INDIFFERENT;
	}

	@Override
	public long flags()
	{
		return Ability.FLAG_UNHOLY;
	}

	@Override
	protected int canTargetCode()
	{
		return CAN_ITEMS;
	}

	private final static String	localizedDiplayText	= CMLib.lang().L("Newly animate dead");

	@Override
	public String displayText()
	{
		return localizedDiplayText;
	}

	@Override
	public void unInvoke()
	{
		final Physical P=affected;
		super.unInvoke();
		if((P instanceof MOB)&&(this.canBeUninvoked)&&(this.unInvoked))
		{
			if((!P.amDestroyed())&&(((MOB)P).amFollowing()==null))
			{
				final Room R=CMLib.map().roomLocation(P);
				if(!CMLib.law().doesHavePriviledgesHere(invoker(), R))
				{
					if((R!=null)&&(!((MOB)P).amDead()))
						R.showHappens(CMMsg.MSG_OK_ACTION, P,L("<S-NAME> wander(s) off."));
					P.destroy();
				}
			}
		}
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		final int tickSet = super.tickDown;
		if(!super.tick(ticking, tickID))
			return false;
		if(ticking instanceof MOB)
		{
			final MOB mob=(MOB)ticking;
			if(mob.amFollowing() != null)
				super.tickDown = tickSet;
		}
		return true;
	}

	public int getUndeadLevel(final MOB mob, final double baseLvl, final double corpseLevel)
	{
		final ExpertiseLibrary exLib=CMLib.expertises();
		final double deathLoreExpertiseLevel = super.getXLEVELLevel(mob);
		final double appropriateLoreExpertiseLevel = super.getX1Level(mob);
		final double charLevel = mob.phyStats().level();
		final double maxDeathLoreExpertiseLevel = exLib.getHighestListableStageBySkill(mob,ID(),ExpertiseLibrary.XType.LEVEL);
		final double maxApproLoreExpertiseLevel = exLib.getHighestListableStageBySkill(mob,ID(),ExpertiseLibrary.XType.X1);
		double lvl = 0;
		if ((maxApproLoreExpertiseLevel > 0)
		&& (maxDeathLoreExpertiseLevel > 0))
		{
			lvl = (charLevel * appropriateLoreExpertiseLevel / maxApproLoreExpertiseLevel)
					-(baseLvl+4+(2*maxDeathLoreExpertiseLevel));
		}
		if(lvl < 0.0)
			lvl = 0.0;
		lvl += baseLvl + (2*deathLoreExpertiseLevel);
		if(lvl > corpseLevel)
			lvl = corpseLevel;
		return (int)Math.round(lvl);
	}

	public void makeSkeletonFrom(final Room R, final DeadBody body, final MOB mob, final int level)
	{
		String race="a";
		if((body.charStats()!=null)&&(body.charStats().getMyRace()!=null))
		{
			final Race oldRace=body.charStats().getMyRace();
			race=CMLib.english().startWithAorAn(oldRace.name()).toLowerCase();
		}
		String description=body.getMobDescription();
		if(description.trim().length()==0)
			description="It looks dead.";
		else
			description+="\n\rIt also looks dead.";
		final String undeadRace = ((body.charStats()!=null) && (body.charStats().getMyRace() != null) && (body.charStats().getMyRace().useRideClass())) ?
				"GenRideableUndead" : "GenUndead";
		final MOB newMOB=CMClass.getMOB(undeadRace);
		if(body.name().indexOf(L("skeleton"))<0)
		{
			newMOB.setName(L("@x1 skeleton",race));
			newMOB.setDescription(description);
			newMOB.setDisplayText(L("@x1 skeleton is here",race));
		}
		if(mob == null)
			newMOB.basePhyStats().setLevel(body.phyStats().level());
		else
			newMOB.basePhyStats().setLevel(getUndeadLevel(mob,level,body.phyStats().level()));
		newMOB.baseCharStats().setStat(CharStats.STAT_GENDER,body.charStats().getStat(CharStats.STAT_GENDER));
		newMOB.baseCharStats().setMyRace(CMClass.getRace("Skeleton"));
		newMOB.baseCharStats().setBodyPartsFromStringAfterRace(body.charStats().getBodyPartsAsString());
		final Ability P=CMClass.getAbility("Prop_StatTrainer");
		if(P!=null)
		{
			P.setMiscText("NOTEACH STR=16 INT=10 WIS=10 CON=10 DEX=15 CHA=2");
			newMOB.addNonUninvokableEffect(P);
		}
		newMOB.recoverCharStats();
		newMOB.basePhyStats().setAttackAdjustment(CMLib.leveler().getLevelAttack(newMOB));
		newMOB.basePhyStats().setDamage(CMLib.leveler().getLevelMOBDamage(newMOB));
		CMLib.factions().setAlignment(newMOB,Faction.Align.EVIL);
		newMOB.baseState().setHitPoints(15*newMOB.basePhyStats().level());
		newMOB.baseState().setMovement(CMLib.leveler().getLevelMove(newMOB));
		newMOB.basePhyStats().setArmor(CMLib.leveler().getLevelMOBArmor(newMOB));
		newMOB.addNonUninvokableEffect(CMClass.getAbility("Prop_ModExperience","0"));
		newMOB.addTattoo("SYSTEM_SUMMONED");
		newMOB.baseState().setMana(0);
		final Behavior B=CMClass.getBehavior("Aggressive");
		if((mob!=null)&&(B!=null))
		{
			B.setParms("+NAMES \"-"+mob.Name()+"\" -LEVEL +>"+newMOB.basePhyStats().level());
			newMOB.addBehavior(B);
		}
		newMOB.recoverCharStats();
		newMOB.recoverPhyStats();
		newMOB.recoverMaxState();
		newMOB.resetToMaxState();
		newMOB.text();
		newMOB.bringToLife(R,true);
		CMLib.beanCounter().clearZeroMoney(newMOB,null);
		newMOB.setMoneyVariation(0);
		//R.showOthers(newMOB,null,CMMsg.MSG_OK_ACTION,L("<S-NAME> appears!"));
		int it=0;
		while(it<R.numItems())
		{
			final Item item=R.getItem(it);
			if((item!=null)&&(item.container()==body))
			{
				final CMMsg msg2=CMClass.getMsg(newMOB,body,item,CMMsg.MSG_GET,null);
				newMOB.location().send(newMOB,msg2);
				final CMMsg msg4=CMClass.getMsg(newMOB,item,null,CMMsg.MSG_GET,null);
				newMOB.location().send(newMOB,msg4);
				final CMMsg msg3=CMClass.getMsg(newMOB,item,null,CMMsg.MSG_WEAR,null);
				newMOB.location().send(newMOB,msg3);
				if(!newMOB.isMine(item))
					it++;
				else
					it=0;
			}
			else
				it++;
		}
		body.destroy();
		newMOB.setStartRoom(null);
		beneficialAffect(mob,newMOB,0,0);
		R.show(newMOB,null,CMMsg.MSG_OK_ACTION,L("<S-NAME> begin(s) to rise!"));
		R.recoverRoomStats();
	}

	@Override
	public boolean invoke(final MOB mob, final List<String> commands, final Physical givenTarget, final boolean auto, final int asLevel)
	{
		final Physical target=getAnyTarget(mob,commands,givenTarget,Wearable.FILTER_UNWORNONLY);
		if(target==null)
			return false;

		if(target==mob)
		{
			mob.tell(L("@x1 doesn't look dead yet.",target.name(mob)));
			return false;
		}
		if(!(target instanceof DeadBody))
		{
			mob.tell(L("You can't animate that."));
			return false;
		}

		final DeadBody body=(DeadBody)target;
		if(body.isPlayerCorpse()||(body.getMobName().length()==0)
		||((body.charStats()!=null)&&(body.charStats().getMyRace()!=null)&&(body.charStats().getMyRace().racialCategory().equalsIgnoreCase("Undead"))))
		{
			mob.tell(L("You can't animate that."));
			return false;
		}
		if(!super.invoke(mob,commands,givenTarget,auto,asLevel))
			return false;

		final boolean success=proficiencyCheck(mob,0,auto);

		if(success)
		{
			final CMMsg msg=CMClass.getMsg(mob,target,this,verbalCastCode(mob,target,auto),auto?"":L("^S<S-NAME> @x1 to animate <T-NAMESELF> as a skeleton.^?",prayForWord(mob)));
			if(mob.location().okMessage(mob,msg))
			{
				mob.location().send(mob,msg);
				makeSkeletonFrom(mob.location(),body,mob,1);
			}
		}
		else
			return beneficialWordsFizzle(mob,target,L("<S-NAME> @x1 to animate <T-NAMESELF>, but fail(s) miserably.",prayForWord(mob)));

		// return whether it worked
		return success;
	}
}
