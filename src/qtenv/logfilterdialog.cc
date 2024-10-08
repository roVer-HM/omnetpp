//==========================================================================
//  LOGFILTERDIALOG.CC - part of
//
//                     OMNeT++/OMNEST
//            Discrete System Simulation in C++
//
//==========================================================================

/*--------------------------------------------------------------*
  Copyright (C) 1992-2017 Andras Varga
  Copyright (C) 2006-2017 OpenSim Ltd.

  This file is distributed WITHOUT ANY WARRANTY. See the file
  `license' for details on this and other legal matters.
*--------------------------------------------------------------*/

#include "logfilterdialog.h"
#include "ui_logfilterdialog.h"
#include "qtenvapp.h"
#include "qtutil.h"

namespace omnetpp {
namespace qtenv {

void LogFilterDialog::addModuleChildren(cModule *module, QTreeWidgetItem *item, const std::set<int>& excludedModuleIds)
{
    if (module) {
        for (cModule::SubmoduleIterator it(module); !it.end(); ++it) {
            QTreeWidgetItem *childItem = new QTreeWidgetItem(item, QStringList(getTextForModule(*it)));
            item->addChild(childItem);
            childItem->setData(0, Qt::UserRole, QVariant::fromValue((void *)(*it)));

            childItem->setCheckState(0, (excludedModuleIds.count((*it)->getId()) == 1) ? Qt::Unchecked : Qt::Checked);

            addModuleChildren(dynamic_cast<cModule *>(*it), childItem, excludedModuleIds);
        }
    }
}

QString LogFilterDialog::getTextForModule(cModule *module)
{
    return QString(module->getFullName()) + " (" + getObjectShortTypeName(module) + ") (id=" + QString::number(module->getId()) + ")";
}

void LogFilterDialog::onItemChanged(QTreeWidgetItem *item, int column)
{
    int count = item->childCount();
    for (int i = 0; i < count; ++i) {
        item->child(i)->setCheckState(0, item->checkState(0));
    }
}

LogFilterDialog::LogFilterDialog(QWidget *parent, cModule *rootModule, const std::set<int>& excludedModuleIds) :
    QDialog(parent),
    ui(new Ui::logfilterdialog)
{
    ui->setupUi(this);
    setFont(getQtenv()->getBoldFont());

    Q_ASSERT(rootModule);

    QTreeWidgetItem *item = new QTreeWidgetItem(ui->treeWidget, QStringList(getTextForModule(rootModule)));
    ui->treeWidget->addTopLevelItem(item);
    item->setData(0, Qt::UserRole, QVariant::fromValue((void *)rootModule));
    item->setCheckState(0, (excludedModuleIds.count(rootModule->getId()) == 1) ? Qt::Unchecked : Qt::Checked);

    addModuleChildren(rootModule, item, excludedModuleIds);

    connect(ui->treeWidget, SIGNAL(itemChanged(QTreeWidgetItem *, int)), this, SLOT(onItemChanged(QTreeWidgetItem *, int)));

    ui->treeWidget->expandItem(item);
}

LogFilterDialog::~LogFilterDialog()
{
    delete ui;
}

std::set<int> LogFilterDialog::getExcludedModuleIds()
{
    std::set<int> result;

    QTreeWidgetItemIterator it(ui->treeWidget);
    while (*it) {
        if ((*it)->checkState(0) != Qt::Checked)
            result.insert(((cModule *)(((*it)->data(0, Qt::UserRole)).value<void *>()))->getId());
        ++it;
    }

    return result;
}

}  // namespace qtenv
}  // namespace omnetpp

