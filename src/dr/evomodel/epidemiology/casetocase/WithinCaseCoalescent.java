package dr.evomodel.epidemiology.casetocase;

import dr.app.tools.NexusExporter;
import dr.evolution.coalescent.*;
import dr.evolution.tree.FlexibleNode;
import dr.evolution.tree.FlexibleTree;
import dr.evolution.tree.NodeRef;
import dr.evolution.tree.Tree;
import dr.evolution.util.Taxon;
import dr.evolution.util.TaxonList;
import dr.evomodel.coalescent.DemographicModel;
import dr.evomodel.tree.TreeModel;
import dr.inference.model.Model;
import dr.inference.model.Parameter;
import dr.inference.model.Variable;
import dr.math.*;
import dr.math.distributions.NormalGammaDistribution;
import dr.math.functionEval.GammaFunction;
import dr.xml.*;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

/**
 * Intended to replace the tree prior; each partition is considered a tree in its own right generated by a
 * coalescent process
 *
 * @author Matthew Hall
 */

public class WithinCaseCoalescent extends CaseToCaseTreeLikelihood {

    public static final String WITHIN_CASE_COALESCENT = "withinCaseCoalescent";
    private Double[] partitionTreeLogLikelihoods;
    private Double[] storedPartitionTreeLogLikelihoods;
    private Double[] timingLogLikelihoods;
    private Double[] storedTimingLogLikelihoods;
    private TreePlusRootBranchLength[] partitionsAsTrees;
    private TreePlusRootBranchLength[] storedPartitionsAsTrees;
    private DemographicModel demoModel;


    public WithinCaseCoalescent(TreeModel virusTree, AbstractOutbreak caseData, String startingNetworkFileName,
                                Parameter infectionTimeBranchPositions, Parameter maxFirstInfToRoot,
                                DemographicModel demoModel)
            throws TaxonList.MissingTaxonException {
        this(virusTree, caseData, startingNetworkFileName, infectionTimeBranchPositions, null,
                maxFirstInfToRoot, demoModel);
    }

    public WithinCaseCoalescent(TreeModel virusTree, AbstractOutbreak caseData, String startingNetworkFileName,
                                Parameter infectionTimeBranchPositions, Parameter infectiousTimePositions,
                                Parameter maxFirstInfToRoot, DemographicModel demoModel)
            throws TaxonList.MissingTaxonException {
        super(WITHIN_CASE_COALESCENT, virusTree, caseData, infectionTimeBranchPositions, infectiousTimePositions,
                maxFirstInfToRoot);
        this.demoModel = demoModel;
        addModel(demoModel);
        partitionTreeLogLikelihoods = new Double[noTips];
        storedPartitionTreeLogLikelihoods = new Double[noTips];
        timingLogLikelihoods = new Double[noTips];
        storedTimingLogLikelihoods = new Double[noTips];

        partitionsAsTrees = new TreePlusRootBranchLength[caseData.size()];
        storedPartitionsAsTrees = new TreePlusRootBranchLength[caseData.size()];

        prepareTree(startingNetworkFileName);

        prepareTimings();
    }

    public static double[] logOfAllValues(double[] values){
        double[] out = Arrays.copyOf(values, values.length);

        for(int i=0; i<values.length; i++){
            out[i] = Math.log(out[i]);
        }
        return out;
    }

    protected double calculateLogLikelihood(){

        if(DEBUG){
            super.debugOutputTree("bleh.nex", false);
        }

        double logL = 0;

        super.prepareTimings();

        int noInfectiousCategories = ((WithinCaseCategoryOutbreak)cases).getInfectiousCategoryCount();

        ArrayList<String> infectiousCategories = ((WithinCaseCategoryOutbreak)cases).getInfectiousCategories();

        ArrayList<ArrayList<Double>> infectiousPeriodsByCategory = new ArrayList<ArrayList<Double>>();

        for(int i=0; i<noInfectiousCategories; i++){
            infectiousPeriodsByCategory.add(new ArrayList<Double>());
        }

        for(AbstractCase aCase : cases.getCases()){
            String category = ((WithinCaseCategoryOutbreak)cases).getInfectiousCategory(aCase);

            ArrayList<Double> correspondingList
                    = infectiousPeriodsByCategory.get(infectiousCategories.indexOf(category));

            correspondingList.add(getInfectiousPeriod(aCase));
        }

        for(int i=0; i<noInfectiousCategories; i++){
            ArrayList<Double> infPeriodsInThisCategory = infectiousPeriodsByCategory.get(i);

            double count = (double)infPeriodsInThisCategory.size();

            NormalGammaDistribution prior = ((WithinCaseCategoryOutbreak)cases)
                    .getInfectiousCategoryPrior(((WithinCaseCategoryOutbreak)cases).getInfectiousCategories().get(i));

            double[] infPredictiveDistributionParameters=prior.getParameters();

            double mu_0 = infPredictiveDistributionParameters[0];
            double lambda_0 = infPredictiveDistributionParameters[1];
            double alpha_0 = infPredictiveDistributionParameters[2];
            double beta_0 = infPredictiveDistributionParameters[3];

            double lambda_n = lambda_0 + count;
            double alpha_n = alpha_0 + count/2;
            double sum = 0;
            for (Double infPeriod : infPeriodsInThisCategory) {
                sum += infPeriod;
            }
            double mean = sum/count;

            double sumOfDifferences = 0;
            for (Double infPeriod : infPeriodsInThisCategory) {
                sumOfDifferences += Math.pow(infPeriod-mean,2);
            }

            double mu_n = (lambda_0*mu_0 + sum)/(lambda_0 + count);
            double beta_n = beta_0 + 0.5*sumOfDifferences + lambda_0*count*Math.pow(mean-mu_0, 2)/(2*(lambda_0+count));

            double priorPredictiveProbability
                    = GammaFunction.logGamma(alpha_n)
                    - GammaFunction.logGamma(alpha_0)
                    + alpha_0*Math.log(beta_0)
                    - alpha_n*Math.log(beta_n)
                    + 0.5*Math.log(lambda_0)
                    - 0.5*Math.log(lambda_n)
                    - (count/2)*Math.log(2*Math.PI);

            logL += priorPredictiveProbability;

            //todo log the parameters of the "posterior"
        }

        if(hasLatentPeriods){
            int noLatentCategories = ((WithinCaseCategoryOutbreak)cases).getLatentCategoryCount();

            ArrayList<String> latentCategories = ((WithinCaseCategoryOutbreak)cases).getLatentCategories();

            ArrayList<ArrayList<Double>> latentPeriodsByCategory = new ArrayList<ArrayList<Double>>();

            for(int i=0; i<noLatentCategories; i++){
                latentPeriodsByCategory.add(new ArrayList<Double>());
            }

            for(AbstractCase aCase : cases.getCases()){
                String category = ((WithinCaseCategoryOutbreak)cases).getLatentCategory(aCase);

                ArrayList<Double> correspondingList
                        = latentPeriodsByCategory.get(latentCategories.indexOf(category));

                correspondingList.add(getLatentPeriod(aCase));
            }

            for(int i=0; i<noLatentCategories; i++){
                ArrayList<Double> latPeriodsInThisCategory = latentPeriodsByCategory.get(i);

                double count = (double)latPeriodsInThisCategory.size();

                NormalGammaDistribution prior = ((WithinCaseCategoryOutbreak)cases)
                        .getLatentCategoryPrior(((WithinCaseCategoryOutbreak) cases).getLatentCategories().get(i));

                double[] latPredictiveDistributionParameters=prior.getParameters();

                double mu_0 = latPredictiveDistributionParameters[0];
                double lambda_0 = latPredictiveDistributionParameters[1];
                double alpha_0 = latPredictiveDistributionParameters[2];
                double beta_0 = latPredictiveDistributionParameters[3];

                double lambda_n = lambda_0 + count;
                double alpha_n = alpha_0 + count/2;
                double sum = 0;
                for (Double latPeriod : latPeriodsInThisCategory) {
                    sum += latPeriod;
                }
                double mean = sum/count;

                double sumOfDifferences = 0;
                for (Double latPeriod : latPeriodsInThisCategory) {
                    sumOfDifferences += Math.pow(latPeriod-mean,2);
                }

                double mu_n = (lambda_0*mu_0 + sum)/(lambda_0 + count);
                double beta_n = beta_0 + 0.5*sumOfDifferences + lambda_0*count*Math.pow(mean-mu_0, 2)
                        /(2*(lambda_0+count));

                double priorPredictiveProbability
                        = GammaFunction.logGamma(alpha_n)
                        - GammaFunction.logGamma(alpha_0)
                        + alpha_0*Math.log(beta_0)
                        - alpha_n*Math.log(beta_n)
                        + 0.5*Math.log(lambda_0)
                        - 0.5*Math.log(lambda_n)
                        - (count/2)*Math.log(2*Math.PI);

                logL += priorPredictiveProbability;

                //todo log the parameters of the "posterior"
            }

        }

        explodeTree();

        for(AbstractCase aCase : cases.getCases()){

            //todo weights (and remember if a weight is zero then the return value should be -INF)

            int number = cases.getCaseIndex(aCase);

            if(timingLogLikelihoods[number]==null){
                double infectionTime = getInfectionTime(aCase);
                AbstractCase parent = getInfector(aCase);
                if(parent!=null &&
                        (getInfectiousTime(parent)>infectionTime
                                || parent.culledYet(infectionTime))) {
                    timingLogLikelihoods[number] = Double.NEGATIVE_INFINITY;
                } else {
                    int possibleParents = 0;
                    for(int i=0; i<cases.size(); i++){
                        AbstractCase parentCandidate = cases.getCase(i);

                        if(i!=number && getInfectiousTime(parentCandidate)<infectionTime
                                && !parentCandidate.culledYet(infectionTime)){
                            possibleParents++;
                        }
                    }
                    if(possibleParents>1){
                        timingLogLikelihoods[number] = -Math.log(possibleParents);
                    } else {
                        timingLogLikelihoods[number] = 0.0;
                    }
                }
            }

            logL += timingLogLikelihoods[number];


            // and then the little tree calculations

            HashSet<AbstractCase> children = getInfectees(aCase);

            if(partitionTreeLogLikelihoods[number]==null){
                TreePlusRootBranchLength treelet = partitionsAsTrees[number];

                if(DEBUG && treelet.getNodeCount()>1){
                    debugTreelet(treelet, aCase+"_partition.nex");
                }

                if(children.size()!=0){
                    MaxTMRCACoalescent coalescent = new MaxTMRCACoalescent(treelet, demoModel,
                            treelet.getRootHeight()+treelet.getRootBranchLength());
                    partitionTreeLogLikelihoods[number] = coalescent.calculateLogLikelihood();
                    logL += partitionTreeLogLikelihoods[number];
                    if(partitionTreeLogLikelihoods[number]==Double.POSITIVE_INFINITY && DEBUG){
                        debugOutputTree("infCoalescent.nex", false);
                    }
                } else {
                    partitionTreeLogLikelihoods[number] = 0.0;
                }
            } else {
                logL += partitionTreeLogLikelihoods[number];
            }
        }
        likelihoodKnown = true;

        if(DEBUG){
            debugOutputTree("outstandard.nex", false);
            debugOutputTree("outfancy.nex", true);
        }

        return logL;


    }

    public void storeState(){
        super.storeState();
        storedPartitionsAsTrees = Arrays.copyOf(partitionsAsTrees, partitionsAsTrees.length);
        storedPartitionTreeLogLikelihoods = Arrays.copyOf(storedPartitionTreeLogLikelihoods,
                storedPartitionTreeLogLikelihoods.length);
        storedTimingLogLikelihoods = Arrays.copyOf(timingLogLikelihoods, timingLogLikelihoods.length);
    }

    public void restoreState(){
        super.restoreState();
        partitionsAsTrees = storedPartitionsAsTrees;
        partitionTreeLogLikelihoods = storedPartitionTreeLogLikelihoods;
        timingLogLikelihoods = storedTimingLogLikelihoods;
    }

    protected void handleModelChangedEvent(Model model, Object object, int index) {


        super.handleModelChangedEvent(model, object, index);

        if(model == treeModel){
            // todo this is still not there

            Arrays.fill(partitionsAsTrees, null);
            Arrays.fill(partitionTreeLogLikelihoods, null);

        } else if(model == branchMap){
            ArrayList<AbstractCase> changedPartitions =
                    ((BranchMapModel.BranchMapChangedEvent)object).getCasesToRecalculate();
            for(AbstractCase aCase : changedPartitions){
                partitionsAsTrees[cases.getCaseIndex(aCase)] = null;
                partitionTreeLogLikelihoods[cases.getCaseIndex(aCase)] = null;
            }
        } else if(model == demoModel){
            Arrays.fill(partitionTreeLogLikelihoods, null);
        }
        Arrays.fill(timingLogLikelihoods, null);
    }

    protected void handleVariableChangedEvent(Variable variable, int index, Parameter.ChangeType type) {


        super.handleVariableChangedEvent(variable, index, type);

        if(variable == infectionTimeBranchPositions){
            partitionTreeLogLikelihoods[index]=null;
            partitionsAsTrees[index]=null;
            if(hasLatentPeriods){
                AbstractCase parent = getInfector(cases.getCase(index));
                if(parent!=null){
                    int parentIndex = cases.getCaseIndex(parent);
                    partitionTreeLogLikelihoods[parentIndex] = null;
                    partitionsAsTrees[parentIndex] = null;
                }
            }
        }

        if(variable == infectionTimeBranchPositions || variable == infectiousTimePositions){
            Arrays.fill(timingLogLikelihoods, null);
        }
    }

    public void makeDirty(){
        super.makeDirty();
        Arrays.fill(partitionTreeLogLikelihoods, null);
        Arrays.fill(timingLogLikelihoods, null);
        Arrays.fill(partitionsAsTrees, null);

    }

    // Tears the tree into small pieces. Indexes correspond to indexes in the outbreak.
    // todo Work out when components of this are unchanged after PT or TT moves

    private void explodeTree(){
        if(DEBUG){
            debugOutputTree("test.nex", false);
        }
        for(int i=0; i<cases.size(); i++){
            if(partitionsAsTrees[i]==null){
                AbstractCase aCase = cases.getCase(i);

                NodeRef partitionRoot = getEarliestNodeInPartition(aCase);

                double infectionTime = getInfectionTime(branchMap.get(partitionRoot.getNumber()));
                double rootTime = getNodeTime(partitionRoot);

                FlexibleNode newRoot = new FlexibleNode();

                FlexibleTree littleTree = new FlexibleTree(newRoot);
                littleTree.beginTreeEdit();

                if(!treeModel.isExternal(partitionRoot)){
                    for(int j=0; j<treeModel.getChildCount(partitionRoot); j++){
                        copyPartitionToLittleTree(littleTree, treeModel.getChild(partitionRoot, j), newRoot, aCase);
                    }
                }

                littleTree.endTreeEdit();

                littleTree.resolveTree();

                partitionsAsTrees[i] = new TreePlusRootBranchLength(littleTree, rootTime - infectionTime);
            }
        }
    }

    public ArrayList<AbstractCase> postOrderTransmissionTreeTraversal(){
        return traverseTransmissionTree(branchMap.get(treeModel.getRoot().getNumber()));
    }

    private ArrayList<AbstractCase> traverseTransmissionTree(AbstractCase aCase){
        ArrayList<AbstractCase> out = new ArrayList<AbstractCase>();
        HashSet<AbstractCase> children = getInfectees(aCase);
        for(int i=0; i<getOutbreak().size(); i++){
            AbstractCase possibleChild = getOutbreak().getCase(i);
            // easiest way to maintain the set ordering of the cases?
            if(children.contains(possibleChild)){
                out.addAll(traverseTransmissionTree(possibleChild));
            }
        }
        out.add(aCase);
        return out;
    }

    private void copyPartitionToLittleTree(FlexibleTree littleTree, NodeRef oldNode, NodeRef newParent,
                                           AbstractCase partition){
        if(branchMap.get(oldNode.getNumber())==partition){
            if(treeModel.isExternal(oldNode)){
                NodeRef newTip = new FlexibleNode(new Taxon(treeModel.getNodeTaxon(oldNode).getId()));
                littleTree.addChild(newParent, newTip);
                littleTree.setBranchLength(newTip, treeModel.getBranchLength(oldNode));
            } else {
                NodeRef newChild = new FlexibleNode();
                littleTree.addChild(newParent, newChild);
                littleTree.setBranchLength(newChild, treeModel.getBranchLength(oldNode));
                for(int i=0; i<treeModel.getChildCount(oldNode); i++){
                    copyPartitionToLittleTree(littleTree, treeModel.getChild(oldNode, i), newChild, partition);
                }
            }
        } else {
            // we need a new tip
            NodeRef transmissionTip = new FlexibleNode(
                    new Taxon("Transmission_"+branchMap.get(oldNode.getNumber()).getName()));
            double parentTime = getNodeTime(treeModel.getParent(oldNode));
            double childTime = getInfectionTime(branchMap.get(oldNode.getNumber()));
            littleTree.addChild(newParent, transmissionTip);
            littleTree.setBranchLength(transmissionTip, childTime - parentTime);

        }
    }

    private class TreePlusRootBranchLength extends FlexibleTree {

        private double rootBranchLength;

        private TreePlusRootBranchLength(FlexibleTree tree, double rootBranchLength){
            super(tree);
            this.rootBranchLength = rootBranchLength;
        }

        private double getRootBranchLength(){
            return rootBranchLength;
        }

        private void setRootBranchLength(double rootBranchLength){
            this.rootBranchLength = rootBranchLength;
        }
    }

    private class MaxTMRCACoalescent extends Coalescent {

        private double maxHeight;

        private MaxTMRCACoalescent(Tree tree, DemographicModel demographicModel, double maxHeight){
            super(tree, demographicModel.getDemographicFunction());

            this.maxHeight = maxHeight;

        }

        public double calculateLogLikelihood() {
            return calculatePartitionTreeLogLikelihood(getIntervals(), getDemographicFunction(), 0, maxHeight);
        }

    }

    public static double calculatePartitionTreeLogLikelihood(IntervalList intervals,
                                                             DemographicFunction demographicFunction, double threshold,
                                                             double maxHeight) {

        double logL = 0.0;

        double startTime = -maxHeight;
        final int n = intervals.getIntervalCount();

        //TreeIntervals sets up a first zero-length interval with a lineage count of zero - skip this one

        for (int i = 0; i < n; i++) {

            // time zero corresponds to the date of first infection

            final double duration = intervals.getInterval(i);
            final double finishTime = startTime + duration;

            final double intervalArea = demographicFunction.getIntegral(startTime, finishTime);
            double normalisationArea = demographicFunction.getIntegral(startTime, 0);

            if( intervalArea == 0 && duration != 0 ) {
                return Double.NEGATIVE_INFINITY;
            }
            final int lineageCount = intervals.getLineageCount(i);

            if(lineageCount>=2){

                final double kChoose2 = Binomial.choose2(lineageCount);

                if (intervals.getIntervalType(i) == IntervalType.COALESCENT) {

                    logL += -kChoose2 * intervalArea;

                    final double demographicAtCoalPoint = demographicFunction.getDemographic(finishTime);

                    if( duration == 0.0 || demographicAtCoalPoint * (intervalArea/duration) >= threshold ) {
                        logL -= Math.log(demographicAtCoalPoint);
                    } else {
                        return Double.NEGATIVE_INFINITY;
                    }

                } else {
                    double numerator = Math.exp(-kChoose2 * intervalArea) - Math.exp(-kChoose2 * normalisationArea);
                    logL += Math.log(numerator);

                }

                // normalisation

                double logDenominator = Math.log1p(-Math.exp(-kChoose2 * normalisationArea));

                logL -= logDenominator;

            }

            startTime = finishTime;
        }

        return logL;
    }

    public void debugTreelet(Tree treelet, String fileName){
        try{
            FlexibleTree treeCopy = new FlexibleTree(treelet);
            for(int j=0; j<treeCopy.getNodeCount(); j++){
                FlexibleNode node = (FlexibleNode)treeCopy.getNode(j);
                node.setAttribute("Number", node.getNumber());
            }
            NexusExporter testTreesOut = new NexusExporter(new PrintStream(fileName));
            testTreesOut.exportTree(treeCopy);
        } catch (IOException ignored) {System.out.println("IOException");}
    }


    //************************************************************************
    // AbstractXMLObjectParser implementation
    //************************************************************************

    public static XMLObjectParser PARSER = new AbstractXMLObjectParser() {
        public static final String STARTING_NETWORK = "startingNetwork";
        public static final String INFECTION_TIMES = "infectionTimeBranchPositions";
        public static final String INFECTIOUS_TIMES = "infectiousTimePositions";
        public static final String MAX_FIRST_INF_TO_ROOT = "maxFirstInfToRoot";
        public static final String DEMOGRAPHIC_MODEL = "demographicModel";

        public String getParserName() {
            return WITHIN_CASE_COALESCENT;
        }

        public Object parseXMLObject(XMLObject xo) throws XMLParseException {

            TreeModel virusTree = (TreeModel) xo.getChild(TreeModel.class);

            String startingNetworkFileName=null;

            if(xo.hasChildNamed(STARTING_NETWORK)){
                startingNetworkFileName = (String) xo.getElementFirstChild(STARTING_NETWORK);
            }

            AbstractOutbreak caseSet = (AbstractOutbreak) xo.getChild(AbstractOutbreak.class);

            CaseToCaseTreeLikelihood likelihood;

            Parameter infectionTimes = (Parameter) xo.getElementFirstChild(INFECTION_TIMES);

            Parameter infectiousTimes = xo.hasChildNamed(INFECTIOUS_TIMES)
                    ? (Parameter) xo.getElementFirstChild(INFECTIOUS_TIMES) : null;

            Parameter earliestFirstInfection = (Parameter) xo.getElementFirstChild(MAX_FIRST_INF_TO_ROOT);

            DemographicModel demoModel = (DemographicModel) xo.getElementFirstChild(DEMOGRAPHIC_MODEL);

            try {
                likelihood = new WithinCaseCoalescent(virusTree, caseSet, startingNetworkFileName, infectionTimes,
                        infectiousTimes, earliestFirstInfection, demoModel);
            } catch (TaxonList.MissingTaxonException e) {
                throw new XMLParseException(e.toString());
            }

            return likelihood;
        }

        public String getParserDescription() {
            return "This element provides a tree prior for a partitioned tree, with each partitioned tree generated" +
                    "by a coalescent process";
        }

        public Class getReturnType() {
            return WithinCaseCoalescent.class;
        }

        public XMLSyntaxRule[] getSyntaxRules() {
            return rules;
        }

        private final XMLSyntaxRule[] rules = {
                new ElementRule(TreeModel.class, "The tree"),
                new ElementRule(WithinCaseCategoryOutbreak.class, "The set of cases"),
                new ElementRule("startingNetwork", String.class, "A CSV file containing a specified starting network",
                        true),
                new ElementRule(MAX_FIRST_INF_TO_ROOT, Parameter.class, "The maximum time from the first infection to" +
                        "the root node"),
                new ElementRule(INFECTION_TIMES, Parameter.class),
                new ElementRule(INFECTIOUS_TIMES, Parameter.class, "For each case, proportions of the time between " +
                        "infection and first event that requires infectiousness (further infection or cull)" +
                        "that has elapsed before infectiousness", true),
                new ElementRule(DEMOGRAPHIC_MODEL, DemographicModel.class, "The demographic model for within-case" +
                        "evolution")
        };
    };
}
