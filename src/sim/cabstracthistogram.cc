//=========================================================================
//  CABSTRACTHISTOGRAM.CC - part of
//
//                  OMNeT++/OMNEST
//           Discrete System Simulation in C++
//
//   Authors: Andras Varga
//
//=========================================================================

/*--------------------------------------------------------------*
  Copyright (C) 1992-2017 Andras Varga
  Copyright (C) 2006-2017 OpenSim Ltd.

  This file is distributed WITHOUT ANY WARRANTY. See the file
  `license' for details on this and other legal matters.
*--------------------------------------------------------------*/

#include <cstdio>
#include <cstring>
#include <cmath>
#include <sstream>
#include <algorithm>
#include "omnetpp/globals.h"
#include "omnetpp/cabstracthistogram.h"
#include "omnetpp/cexception.h"
#include "omnetpp/distrib.h"

#ifdef WITH_PARSIM
#include "omnetpp/ccommbuffer.h"
#endif

using namespace std;

namespace omnetpp {

std::vector<double> cAbstractHistogram::getBinEdges() const
{
    int n = getNumBins()+1;
    std::vector<double> result(n);
    for (int i = 0; i < n; i++)
        result[i] = getBinEdge(i);
    return result;
}

std::vector<double> cAbstractHistogram::getBinValues() const
{
    int n = getNumBins();
    std::vector<double> result(n);
    for (int i = 0; i < n; i++)
        result[i] = getBinValue(i);
    return result;
}

cAbstractHistogram::Bin cAbstractHistogram::getBinInfo(int k) const
{
    if (k < 0 || k >= getNumBins())
        return Bin();
    Bin c;
    c.lower = getBinEdge(k);
    c.upper = getBinEdge(k+1);
    c.value = getBinValue(k);
    c.relativeFreq = getBinPDF(k);
    return c;
}

double cAbstractHistogram::getPDF(double x) const
{
    if (!binsAlreadySetUp())
        throw cRuntimeError(this, "");

    if (x < getMin())
        return 0;

    if (x < getBinEdge(0))
        return getUnderflowSumWeights() / getSumWeights() / (getBinEdge(0) - getMin());

    int numBins = getNumBins();

    // returns 0..1; assumes constant PDF within a bin
    for (int i = 0; i < numBins; ++i) {
        double lowerEdge = getBinEdge(i);
        double upperEdge = getBinEdge(i + 1);

        if (x < upperEdge)
            return getBinValue(i) / getSumWeights() / (upperEdge-lowerEdge);
    }

    if (x < getMax())
        return getOverflowSumWeights() / getSumWeights() / (getMax() - getBinEdge(numBins));

    return 0;
}

double cAbstractHistogram::getCDF(double x) const
{
    if (x < getMin())
        return 0;

    if (x < getBinEdge(0))
        return getUnderflowSumWeights() * ((x - getMin()) / getSumWeights() / (getBinEdge(0) - getMin()));

    int numBins = getNumBins();

    // returns 0..1; uses linear approximation between two markers
    for (int i = 0; i < numBins; ++i) {
        double lowerEdge = getBinEdge(i);
        double upperEdge = getBinEdge(i + 1);

        if (x < upperEdge)
            return getBinValue(i) * ((x - lowerEdge) / getSumWeights() / (upperEdge - lowerEdge));
    }

    if (x < getMax())
        return getOverflowSumWeights() * ((x - getBinEdge(numBins)) / getSumWeights() / (getMax() - getBinEdge(numBins)));

    return 1;
}

const cAbstractHistogram::Bin& cAbstractHistogram::internalGetBinInfo(int k) const
{
    // only for use in sim_std.msg (each call overwrites the static buffer!)
    static Bin buf;
    buf = getBinInfo(k);
    return buf;
}

double cAbstractHistogram::getBinPDF(int k) const
{
    if (numValues == 0)
        return 0.0;
    double binSize = getBinEdge(k+1) - getBinEdge(k);
    return binSize == 0 ? 0.0 : getBinValue(k) / binSize / getSumWeights();
}

double cAbstractHistogram::draw() const
{
    double binValueSum = getSumWeights();
    double rand = uniform(getRNG(), 0, binValueSum);

    if (rand < getUnderflowSumWeights())
        return uniform(getRNG(), getMin(), getBinEdge(0));

    rand -= getUnderflowSumWeights();

    // selecting a bin, each with a probability proportional to its value
    for (int i = 0; i < getNumBins(); ++i) {
        double binValue = getBinValue(i);
        if (rand < binValue)
            // we can't do better than uniform within a single bin
            return uniform(getRNG(), getBinEdge(i), getBinEdge(i + 1));
        else
            // we're not yet at the selected bin yet
            rand -= binValue;
    }

    return uniform(getRNG(), getBinEdge(getNumBins()), getMax());
}

}  // namespace omnetpp

