/*
 * ASRSubstitutionModelConvolutionStatisticParser.java
 *
 * Copyright (c) 2002-2021 Alexei Drummond, Andrew Rambaut and Marc Suchard
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

package dr.evomodelxml.treedatalikelihood;

        import dr.evolution.alignment.PatternList;
        import dr.evolution.tree.TreeUtils;
        import dr.evolution.util.Taxa;
        import dr.evolution.util.TaxonList;
        import dr.evomodel.branchratemodel.BranchRateModel;
        import dr.evomodel.substmodel.GLMSubstitutionModel;
        import dr.evomodel.substmodel.SubstitutionModel;
        import dr.evomodel.treedatalikelihood.discrete.ASRConvolutionRandomEffectsDynamicStatespaceStatistic;
        import dr.evomodel.treedatalikelihood.discrete.ASRSubstitutionModelConvolutionStatistic;
        import dr.evomodel.treedatalikelihood.discrete.SequenceDistanceStatistic;
        import dr.evomodel.treelikelihood.AncestralStateBeagleTreeLikelihood;
        import dr.inference.distribution.DistributionLikelihood;
        import dr.inference.distribution.GammaDistributionModel;
        import dr.inference.distribution.ParametricDistributionModel;
        import dr.inference.model.Parameter;
        import dr.inference.model.Statistic;
        import dr.math.distributions.GammaDistribution;
        import dr.oldevomodelxml.treelikelihood.TreeLikelihoodParser;
        import dr.xml.*;

        import static dr.evomodelxml.tree.MonophylyStatisticParser.parseTaxonListOrTaxa;

/**
 */
public class ASRConvolutionRandomEffectsDynamicStatespaceStatisticParser extends AbstractXMLObjectParser {

    public static String STATISTIC = "hackyStateSpaceConvolutionStatistic";
    public static String NUC1 = "firstNucleotide";
    public static String NUC2 = "secondNucleotide";
    public static String DINUC_EFFECTS = "dinucleotideEffects";
    public static String SUBS_MODEL_ANCESTOR = "substitutionModelAncestor";
    public static String SUBS_MODEL_DESCENDANT = "substitutionModelDescendant";
    public static String RATE_ANCESTOR = "rateAncestor";
    public static String RATE_DESCENDANT = "rateDescendant";
    private static final String MRCA = "mrca";
    public static final String TAXA = "taxa";
    public static final String PRIOR = "prior";

    public String getParserName() { return STATISTIC; }

    public Object parseXMLObject(XMLObject xo) throws XMLParseException {
        String name = xo.getAttribute(Statistic.NAME, xo.getId());

        AncestralStateBeagleTreeLikelihood asrLike = (AncestralStateBeagleTreeLikelihood) xo.getChild(AncestralStateBeagleTreeLikelihood.class);

        SubstitutionModel subsModelAncestor = null;
        if (xo.hasChildNamed(SUBS_MODEL_ANCESTOR)) {
            subsModelAncestor = (SubstitutionModel) xo.getChild(SUBS_MODEL_ANCESTOR).getChild(0);
        }

        SubstitutionModel subsModelDescendant = null;
        if (xo.hasChildNamed(SUBS_MODEL_DESCENDANT)) {
            subsModelDescendant = (SubstitutionModel) xo.getChild(SUBS_MODEL_DESCENDANT).getChild(0);
        }

        BranchRateModel branchRates = (BranchRateModel)xo.getChild(BranchRateModel.class);

        TaxonList mrcaTaxa = null;
        if (xo.hasChildNamed(MRCA)) {
            mrcaTaxa = parseTaxonListOrTaxa(xo.getChild(MRCA));
        }

        ParametricDistributionModel prior = null;
        if ( xo.hasChildNamed(PRIOR) ) {
            prior = (ParametricDistributionModel) xo.getElementFirstChild(PRIOR);
        }

        Statistic rateAncestor = null;
        if (xo.hasChildNamed(RATE_ANCESTOR)) {
            rateAncestor = (Statistic) xo.getChild(RATE_ANCESTOR).getChild(0);
            if (rateAncestor.getDimension() != 1) {
                throw new RuntimeException("If providing ancestor rate, it must be a 1-dimensional statistic.");
            }

        }

        Statistic rateDescendant = null;
        if (xo.hasChildNamed(RATE_DESCENDANT)) {
            rateDescendant = (Statistic) xo.getChild(RATE_DESCENDANT).getChild(0);
            if (rateDescendant.getDimension() != 1) {
                throw new RuntimeException("If providing descendent rate, it must be a 1-dimensional statistic.");
            }

        }

        Parameter firstNuc = (Parameter) xo.getChild(NUC1).getChild(0);
        int nDi = firstNuc.getParameterValues().length;
        int[] firstNucleotide = new int[nDi];
        for (int i = 0; i < nDi; i++) {
            firstNucleotide[i] = (int) firstNuc.getParameterValue(i);
        }

        Parameter secondNuc = (Parameter) xo.getChild(NUC1).getChild(0);
        if ( secondNuc.getParameterValues().length != nDi) { throw new RuntimeException("Number of dinucleotides specified in first and second do not match."); }
        int[] secondNucleotide = new int[nDi];
        for (int i = 0; i < nDi; i++) {
            secondNucleotide[i] = (int) secondNuc.getParameterValue(i);
        }

        Parameter dinucleotideEffects = (Parameter) xo.getChild(DINUC_EFFECTS).getChild(0);

//        TaxonList mrcaTaxa = null;
//        if (xo.hasChildNamed(MRCA)) {
//            mrcaTaxa = (TaxonList) xo.getElementFirstChild(MRCA);
//        }

        ASRConvolutionRandomEffectsDynamicStatespaceStatistic stat = null;
        try {
            stat = new ASRConvolutionRandomEffectsDynamicStatespaceStatistic(
                    name,
                    asrLike,
                    subsModelAncestor,
                    subsModelDescendant,
                    dinucleotideEffects,
                    firstNucleotide,
                    secondNucleotide,
                    branchRates,
                    rateAncestor,
                    rateDescendant,
                    mrcaTaxa,
                    prior);
        } catch (TreeUtils.MissingTaxonException e) {
            throw new XMLParseException("Unable to find taxon-set.");
        }

        return stat;
    }

    //************************************************************************
    // AbstractXMLObjectParser implementation
    //************************************************************************

    public String getParserDescription() {
        return "Estimates (via ML or MAP) branch time prior to MRCA at which substitution regime shifts from ancestor to descendant model.";
    }

    public Class getReturnType() { return SequenceDistanceStatistic.class; }

    public XMLSyntaxRule[] getSyntaxRules() { return rules; }

    private XMLSyntaxRule[] rules = new XMLSyntaxRule[] {
            new ElementRule(AncestralStateBeagleTreeLikelihood.class, false),
            new ElementRule(SUBS_MODEL_ANCESTOR, SubstitutionModel.class, "Substitution model for the ancestral portion of the branch, taken as the template for the expanded state-space model.", false),
            new ElementRule(SUBS_MODEL_DESCENDANT, SubstitutionModel.class, "Substitution model for the more recent portion of the branch.", false),
            new ElementRule(DINUC_EFFECTS, Parameter.class, "The (log-scale) random effects added for the expanded statespace on the second half of the branch.", true),
            new ElementRule(BranchRateModel.class, false),
            new ElementRule(RATE_ANCESTOR, Statistic.class, "If provided, this will be used as the evolutionary rate for the ancestral portion of the branch instead of the rate provided by the BranchRateModel.", true),
            new ElementRule(RATE_DESCENDANT, Statistic.class, "If provided, this will be used as the evolutionary rate for the descendant portion of the branch instead of the rate provided by the BranchRateModel.", true),
            new ElementRule(MRCA,
                    new XMLSyntaxRule[]{new ElementRule(Taxa.class)}, false),
            new ElementRule(PRIOR, ParametricDistributionModel.class, "A prior for the convolution time (measured in time before descendant node).", true),
    };

}
