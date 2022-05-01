/*
 * UltrametricSpeciationGradient.java
 *
 * Copyright (c) 2002-2019 Alexei Drummond, Andrew Rambaut and Marc Suchard
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


package dr.evomodel.speciation;

import dr.evolution.tree.Tree;
import dr.evomodel.tree.TreeModel;
import dr.inference.hmc.GradientWrtParameterProvider;
import dr.inference.loggers.LogColumn;
import dr.inference.loggers.Loggable;
import dr.inference.model.Likelihood;
import dr.inference.model.Parameter;
import dr.xml.Reportable;

/**
 * @author Xiang Ji
 * @author Marc Suchard
 */
public class SpeciationLikelihoodGradient implements GradientWrtParameterProvider, Reportable, Loggable {

    private final SpeciationLikelihood likelihood;
    private final Parameter parameter;
    private final WrtParameter wrtParameter;
    private final TreeModel tree;

    private final SpeciationModelGradientProvider provider;

    public SpeciationLikelihoodGradient(SpeciationLikelihood likelihood,
                                        TreeModel tree,
                                        WrtParameter wrtParameter) {

        this.likelihood = likelihood;
        this.tree = tree;
        this.wrtParameter = wrtParameter;
        this.provider = likelihood.getGradientProvider();
        this.parameter = wrtParameter.getParameter(provider, tree);
    }

    @Override
    public Likelihood getLikelihood() {
        return likelihood;
    }

    @Override
    public Parameter getParameter() {
        return parameter;
    }

    @Override
    public int getDimension() {
        return parameter.getDimension();
    }

    @Override
    public double[] getGradientLogDensity() {
        return wrtParameter.getGradientLogDensity(provider, tree);
    }

    public TreeModel getTree() {
        return tree;
    }

    @Override
    public LogColumn[] getColumns() {
        return Loggable.getColumnsFromReport(this, "SpeciationLikelihoodGradient check");
    }

    @Override
    public String getReport() {
        return GradientWrtParameterProvider.getReportAndCheckForError(this, 0.0, Double.POSITIVE_INFINITY, 1E-3);
    }

    public enum WrtParameter {
        NODE_HEIGHT("nodeHeight") {
            @Override
            double[] getGradientLogDensity(SpeciationModelGradientProvider provider, Tree tree) {
                double[] gradient = new double[tree.getInternalNodeCount()];

                for (int i = 0; i < tree.getInternalNodeCount(); i++) {
                    gradient[i] = provider.getNodeHeightGradient(tree, tree.getNode(i + tree.getExternalNodeCount()));
                }

                return gradient;
            }

            @Override
            Parameter getParameter(SpeciationModelGradientProvider provider, TreeModel tree) {
                throw new RuntimeException("To be implemented soon");
            }
        },

        BIRTH_RATE("birthRate") {
            @Override
            double[] getGradientLogDensity(SpeciationModelGradientProvider provider, Tree tree) {
                return provider.getBirthRateGradient(tree, null);
            }

            @Override
            Parameter getParameter(SpeciationModelGradientProvider provider, TreeModel tree) {
                return provider.getBirthRateParameter();
            }
        },

        DEATH_RATE("deathRate") {
            @Override
            double[] getGradientLogDensity(SpeciationModelGradientProvider provider, Tree tree) {
                return provider.getDeathRateGradient(tree, null);
            }

            @Override
            Parameter getParameter(SpeciationModelGradientProvider provider, TreeModel tree) {
                return provider.getDeathRateParameter();
            }
        },

        SAMPLING_RATE("samplingRate") {
            @Override
            double[] getGradientLogDensity(SpeciationModelGradientProvider provider, Tree tree) {
                return provider.getSamplingRateGradient(tree, null);
            }

            @Override
            Parameter getParameter(SpeciationModelGradientProvider provider, TreeModel tree) {
                return provider.getSamplingRateParameter();
            }
        },

        TREATMENT_PROBABILITY("treatmentProbability") {
            @Override
            double[] getGradientLogDensity(SpeciationModelGradientProvider provider, Tree tree) {
                return provider.getTreatmentProbabilityGradient(tree, null);
            }

            @Override
            Parameter getParameter(SpeciationModelGradientProvider provider, TreeModel tree) {
                return provider.getTreatmentProbabilityParameter();
            }
        },

        SAMPLING_PROBABILITY("samplingProbability") {
            @Override
            double[] getGradientLogDensity(SpeciationModelGradientProvider provider, Tree tree) {
                return provider.getSamplingProbabilityGradient(tree, null);
            }

            @Override
            Parameter getParameter(SpeciationModelGradientProvider provider, TreeModel tree) {
                return provider.getSamplingProbabilityParameter();
            }
        };

        WrtParameter(String name) {
            this.name = name;
        }

        abstract double[] getGradientLogDensity(SpeciationModelGradientProvider provider, Tree tree);

        abstract Parameter getParameter(SpeciationModelGradientProvider provider, TreeModel tree);

        private final String name;

        public static WrtParameter factory(String match) {
            for (WrtParameter type : WrtParameter.values()) {
                if (match.equalsIgnoreCase(type.name)) {
                    return type;
                }
            }
            return null;
        }
    }
}
