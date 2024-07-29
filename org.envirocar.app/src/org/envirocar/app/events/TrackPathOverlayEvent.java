/**
 * Copyright (C) 2013 - 2021 the enviroCar community
 *
 * This file is part of the enviroCar app.
 *
 * The enviroCar app is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The enviroCar app is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with the enviroCar app. If not, see http://www.gnu.org/licenses/.
 */
package org.envirocar.app.events;

import org.envirocar.map.model.Point;

import java.util.List;

/**
 * @author dewall
 */
public class TrackPathOverlayEvent {

    public final List<Point> points;

    /**
     * Constructor.
     *
     * @param points The list of points recorded as part of the track.
     */
    public TrackPathOverlayEvent(List<Point> points) {
        this.points = points;
    }
}
