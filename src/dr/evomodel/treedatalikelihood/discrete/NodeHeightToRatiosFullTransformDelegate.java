/*
 * NodeHeightToRatiosFullTransformDelegate.java
 *
 * Copyright (c) 2002-2017 Alexei Drummond, Andrew Rambaut and Marc Suchard
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

package dr.evomodel.treedatalikelihood.discrete;

import dr.evolution.tree.NodeRef;
import dr.evomodel.branchratemodel.BranchRateModel;
import dr.evomodel.tree.TreeModel;
import dr.evomodel.treedatalikelihood.DataLikelihoodDelegate;
import dr.evomodel.treedatalikelihood.ProcessOnTreeDelegate;
import dr.inference.model.Bounds;
import dr.inference.model.CompoundParameter;
import dr.inference.model.Parameter;

import java.util.List;

/**
 * @author Marc A. Suchard
 * @author Xiang Ji
 */
public class NodeHeightToRatiosFullTransformDelegate extends NodeHeightToRatiosTransformDelegate {

    private final double maxTipHeight;
    private final Parameter heightParameter;
    private CompoundParameter rootHeightAndRatios;

    public NodeHeightToRatiosFullTransformDelegate(TreeModel treeModel,
                                                   Parameter nodeHeights,
                                                   Parameter ratios,
                                                   BranchRateModel branchRateModel) {

        super(treeModel, nodeHeights, ratios, branchRateModel);
        if (treeModel.getInternalNodeCount() != nodeHeights.getDimension()) {
            throw new RuntimeException("Use all internal node (including root) for this transform.");
        }

        double tmpMaxTipHeight = 0.0;
        for (int i = 0; i < tree.getExternalNodeCount(); i++) {
            double tipHeight = tree.getNodeHeight(tree.getNode(i));
            if (tipHeight > tmpMaxTipHeight) {
                tmpMaxTipHeight = tipHeight;
            }
        }
        this.maxTipHeight = tmpMaxTipHeight;

        this.heightParameter = new HeightParameter(tree,
                new Parameter.DefaultBounds(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1));
        this.rootHeightAndRatios = new CompoundParameter("rootHeightAndRatios",
                new Parameter[]{heightParameter, ratios});

    }

    private class HeightParameter extends Parameter.Proxy {

        private final TreeModel tree;
        private Bounds<Double> bounds = null;

        private HeightParameter(TreeModel tree, Bounds<Double> boundary) {
            super("LocationShiftedRootHeightParameter", 1);
            this.tree = tree;
            addBounds(boundary);
        }

        @Override
        public void addBounds(Bounds<Double> boundary) {
            this.bounds = boundary;
        }

        @Override
        public Bounds<Double> getBounds() {
            return bounds;
        }

        @Override
        public double getParameterValue(int dim) {
            return tree.getNodeHeight(tree.getRoot()) - maxTipHeight;
        }

        @Override
        public void setParameterValue(int dim, double value) {
            tree.setNodeHeight(tree.getRoot(), getRootHeight(value));
        }

        @Override
        public void setParameterValueQuietly(int dim, double value) {
            tree.setNodeHeight(tree.getRoot(), getRootHeight(value));
        }

        @Override
        public void setParameterValueNotifyChangedAll(int dim, double value) {
            tree.setNodeHeight(tree.getRoot(), getRootHeight(value));
        }
    }

    private double getRootHeight(double heightValue) {
        return heightValue + maxTipHeight;
    }

    @Override
    public double[] transform(double[] values) {
        setNodeHeights(values);
        updateRatios();
        return setCombinedValues();
    }

    @Override
    public double[] updateGradientLogDensity(double[] gradient, double[] value) {
        double[] gradientLogDensityWrtRatios = super.updateGradientLogDensity(gradient, value);
        double[] updatedGradient = new double[ratios.getDimension() + 1];
        System.arraycopy(gradientLogDensityWrtRatios, 0, updatedGradient, 1, ratios.getDimension());

        double[] logTime = getLogTimeArray();
        updatedGradient[0] = updateHeightParameterGradientUnweightedLogDensity(gradient) - updateHeightParameterGradientUnweightedLogDensity(logTime);

        return updatedGradient;
    }

//    private double[] processNodeHeights(double[] nodeHeights) {
//        if (nodeHeights.length == tree.getInternalNodeCount() - 1) {
//            return nodeHeights;
//        } else if (nodeHeights.length == tree.getInternalNodeCount()) {
//            double[] processedHeights = new double[tree.getInternalNodeCount() - 1];
//            int tmp = 0;
//            for (int i = 0; i < tree.getInternalNodeCount(); i++) {
//                NodeRef node = tree.getNode(i + tree.getExternalNodeCount());
//                if (!tree.isRoot(node)) {
//                    processedHeights[tmp++] = nodeHeights[i];
//                }
//            }
//            return processedHeights;
//        } else {
//            throw new RuntimeException("Dimension mismatch!");
//        }
//    }

    @Override
    public double[] updateGradientUnWeightedLogDensity(double[] gradient, double[] value, int from, int to) {
        double[] gradientUnWeightedLogDensityWrtRatios = super.updateGradientUnWeightedLogDensity(gradient, value, from, to);
        double[] updatedGradient = new double[ratios.getDimension() + 1];
        updatedGradient[0] = updateHeightParameterGradientUnweightedLogDensity(gradient);
        System.arraycopy(gradientUnWeightedLogDensityWrtRatios, 0, updatedGradient, 1, ratios.getDimension());
        return updatedGradient;
    }

    private double[] setCombinedValues() {

        double[] rootHeightAndRatioArray = new double[ratios.getDimension() + 1];
        System.arraycopy(ratios.getParameterValues(), 0, rootHeightAndRatioArray, 1,
                ratios.getDimension());

        rootHeightAndRatioArray[0] = this.rootHeightAndRatios.getParameterValue(0);

        return rootHeightAndRatioArray;
    }

    @Override
    public double[] inverse(double[] values) {
        this.heightParameter.setParameterValue(0, values[0]);
        double[] ratioValues = separateRatios(values);
        return super.inverse(ratioValues);
    }

    private double[] separateRatios(double[] combinedValues) {
        double[] ratioValues = new double[ratios.getDimension()];
        System.arraycopy(combinedValues, 1, ratioValues, 0, ratioValues.length);
        return ratioValues;
    }

    @Override
    public Parameter getParameter() {
        return rootHeightAndRatios;
    }

    private double updateHeightParameterGradientUnweightedLogDensity(double[] gradient) {
        preOrderTraversal.updateAllNodes();
        preOrderTraversal.dispatchTreeTraversalCollectBranchAndNodeOperations();

        double[] multiplierArray = new double[tree.getInternalNodeCount()];
        final List<DataLikelihoodDelegate.NodeOperation> nodeOperations = preOrderTraversal.getNodeOperations();

        multiplierArray[getNodeHeightGradientIndex(tree.getRoot())] = 1.0;
        for (ProcessOnTreeDelegate.NodeOperation op : nodeOperations) {
            NodeRef node = tree.getNode(op.getLeftChild());
            if (!tree.isRoot(node) && ! tree.isExternal(node)) {
                final double ratio = ratios.getParameterValue(getRatiosIndex(node));
                multiplierArray[getNodeHeightGradientIndex(node)] =
                        ratio * multiplierArray[getNodeHeightGradientIndex(tree.getParent(node))];
            }
        }

        double sum = 0.0;
        for (int i = 0; i < gradient.length; i++) {
            sum += gradient[i] * multiplierArray[i];
        }

        return sum;
    }

}
