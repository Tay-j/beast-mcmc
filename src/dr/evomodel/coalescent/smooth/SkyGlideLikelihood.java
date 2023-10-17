/*
 * SkyGlideLikelihood.java
 *
 * Copyright (c) 2002-2022 Alexei Drummond, Andrew Rambaut and Marc Suchard
 *
 * This file is part of BEAST.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership and licensing.
 *
 * BEAST is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 *  BEAST is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BEAST; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
 * Boston, MA  02110-1301  USA
 */

package dr.evomodel.coalescent.smooth;

import dr.evomodel.bigfasttree.BigFastTreeIntervals;
import dr.evomodel.tree.TreeModel;
import dr.inference.model.AbstractModelLikelihood;
import dr.inference.model.Model;
import dr.inference.model.Parameter;
import dr.inference.model.Variable;
import dr.xml.Reportable;

import java.util.ArrayList;
import java.util.List;

/**
 * A likelihood function for a smooth skygrid coalescent process that nicely works with the newer tree intervals
 *
 * @author Mathieu Fourment
 * @author Erick Matsen
 * @author Xiang Ji
 * @author Marc A. Suchard
 */
public class SkyGlideLikelihood extends AbstractModelLikelihood implements Reportable {

    private final List<TreeModel> trees;

    private final List<BigFastTreeIntervals> intervals;
    private final Parameter logPopSizeParameter;
    private final Parameter gridPointParameter;

    protected SkyGlideLikelihood(String name,
                                 List<TreeModel> trees,
                                 Parameter logPopSizeParameter,
                                 Parameter gridPointParameter) {
        super(name);
        this.trees = trees;
        this.logPopSizeParameter = logPopSizeParameter;
        this.gridPointParameter = gridPointParameter;
        this.intervals = new ArrayList<>();
        for (int i = 0; i < trees.size(); i++) {
            this.intervals.add(new BigFastTreeIntervals(trees.get(0)));
        }
    }

    @Override
    public String getReport() {
        return null;
    }

    @Override
    protected void handleModelChangedEvent(Model model, Object object, int index) {

    }

    @Override
    protected void handleVariableChangedEvent(Variable variable, int index, Parameter.ChangeType type) {

    }

    @Override
    protected void storeState() {

    }

    @Override
    protected void restoreState() {

    }

    @Override
    protected void acceptState() {

    }

    @Override
    public Model getModel() {
        return null;
    }

    @Override
    public double getLogLikelihood() {
        double lnL = 0;
        for (int i = 0; i < trees.size(); i++) {
            lnL += getSingleTreeLogLikelihood(i);
        }
        return lnL;
    }

    public double getSingleTreeLogLikelihood(int index) {
        BigFastTreeIntervals interval = intervals.get(index);
        TreeModel tree = trees.get(index);
        int currentGridIndex = 0;
        double lnL = 0;
        for (int i = 0; i < interval.getIntervalCount() - 1; i++) {
            final double intervalStart = interval.getIntervalTime(i);
            final double intervalEnd = interval.getIntervalTime(i + 1);
            final int lineageCount = interval.getLineageCount(i);
            int[] gridIndices = getGridPoints(index, currentGridIndex, intervalStart, intervalEnd);
            final int firstGridIndex = gridIndices[0];
            final int lastGridIndex = gridIndices[1];
            if (firstGridIndex == Integer.MAX_VALUE) { // no grid points within interval
                lnL += 0.5 * lineageCount * (lineageCount - 1) * getLinearInverseIntegral(intervalStart, intervalEnd, currentGridIndex);
            } else {
                double sum = 0;
                sum += getLinearInverseIntegral(intervalStart, gridPointParameter.getParameterValue(firstGridIndex), currentGridIndex);
                currentGridIndex = firstGridIndex;
                while(currentGridIndex < lastGridIndex) {
                    sum += getLinearInverseIntegral(gridPointParameter.getParameterValue(currentGridIndex), gridPointParameter.getParameterValue(currentGridIndex + 1), currentGridIndex);
                    currentGridIndex++;
                }
                sum += getLinearInverseIntegral(gridPointParameter.getParameterValue(currentGridIndex), intervalEnd, currentGridIndex);
                lnL += 0.5 * lineageCount * (lineageCount - 1) * sum;
            }
        }
        return lnL;
    }

    private double getLinearInverseIntegral(double start, double end, int gridIndex) {
        final double slope = getGridSlope(gridIndex);
        final double intercept = getGridIntercept(gridIndex);
        assert(slope != 0 || intercept != 0);
        if (slope == 0) {
            return (end - start) / intercept;
        } else {
            return (Math.log(slope * end + intercept) - Math.log(slope * start + intercept)) / slope;
        }
    }

    private double getGridSlope(int gridIndex) {
        if (gridIndex == 0 || gridIndex == gridPointParameter.getDimension() - 1) {
            return logPopSizeParameter.getParameterValue(gridIndex + 1) - logPopSizeParameter.getParameterValue(gridIndex);
        }
        return (logPopSizeParameter.getParameterValue(gridIndex + 1) - logPopSizeParameter.getParameterValue(gridIndex))
                / (gridPointParameter.getParameterValue(gridIndex + 1) - gridPointParameter.getParameterValue(gridIndex));
    }

    private double getGridIntercept(int gridIndex) {
        return 0;
    }

    private int[] getGridPoints(int treeIndex, int startGridIndex, double startTime, double endTime) {
        int firstGridIndex = Integer.MAX_VALUE;
        int lastGridIndex = -1;
        int i = startGridIndex;
        double time = gridPointParameter.getParameterValue(i);
        while (time < endTime) {
            if (time >= startTime) {
                if (firstGridIndex > i) firstGridIndex = i;
                if (lastGridIndex < i) lastGridIndex = i;
            }
            i++;
            time = gridPointParameter.getParameterValue(i);
        }
        return new int[]{firstGridIndex, lastGridIndex};
    }

    @Override
    public void makeDirty() {

    }
}
