/*--------------------------------------------------------------*
  Copyright (C) 2006-2008 OpenSim Ltd.
  
  This file is distributed WITHOUT ANY WARRANTY. See the file
  'License' for details on this and other legal matters.
*--------------------------------------------------------------*/

package org.omnetpp.experimental.animation.controller;

public interface IReplayAnimationListener {
	public void controllerStateChanged();
	
	public void replayPositionChanged(AnimationPosition animationPosition);
}