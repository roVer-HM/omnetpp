//==========================================================================
//  CEXCEPTION.H - part of
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

#ifndef __OMNETPP_CEXCEPTION_H
#define __OMNETPP_CEXCEPTION_H

#include <cstdarg>
#include <exception>
#include <stdexcept>
#include <vector>
#include "simkerneldefs.h"
#include "simtime_t.h"
#include "errmsg.h"
#include "clifecyclelistener.h"
#include "cobject.h"

namespace omnetpp {

class cComponent;
class cModule;
class cSimulation;
class cEnvir;

/**
 * This type exists purely for technical reasons: Occurrences of this type on the code
 * should really be replaced with the ErrorCode enum. However, doing so would
 * result in compile-time warnings: variadic constructors taking an enum trigger
 * undefined behavior in C++. Clang 3.9 and newer gives a warning about it.
 *
 * Some related literature:
 * https://www.securecoding.cert.org/confluence/display/cplusplus/EXP58-CPP.+Pass+an+object+of+the+correct+type+to+va_start
 * http://lists.llvm.org/pipermail/cfe-commits/Week-of-Mon-20160822/169004.html
 */
typedef int ErrorCodeInt;

class cRuntimeError;

/**
 * @brief Exception class.
 *
 * @ingroup Fundamentals
 */
class SIM_API cException : public std::exception, public cObject
{
  protected:
    ErrorCode errorCode;
    std::string msg;

    cSimulation *simulation;
    cEnvir *envir;
    int simulationStage;
    eventnumber_t eventNumber;
    simtime_t simtime;
    SimulationLifecycleEventType lifecycleListenerType = LF_NONE;

    bool hasContext_;
    std::string contextClassName;
    std::string contextFullPath;
    int contextComponentId;
    int contextComponentKind; // actually cComponent::ComponentKind

    std::vector<cRuntimeError*> nestedExceptions; // exceptions that occur while handling this exception

    /**
     * Helper function for constructors: assembles and stores the message text.
     * If the first arg is non-nullptr, the message text will be prepended (if needed)
     * with the object type and name, like this: "(cArray)array: ..."
     */
    void init(const cObject *obj, ErrorCode errorcode, const std::string& msg);

    // helper for init()
    void storeContext();

    // default constructor, for subclasses only.
    cException();

    //
    // Helper, called from cException constructors.
    //
    // If an exception occurs in initialization code (during construction of
    // global objects, before main() is called), there is nobody who could
    // catch the error, so it would just cause a program abort.
    // Here we handle this case manually: if cException ctor is invoked before
    // main() has started, we print the error message and call exit(1).
    //
    void exitIfStartupError();

  private:
    void copy(const cException& other);

  public:
    // internal, for use in cSimulation::notifyLifecycleListeners()
    void setLifecycleListenerType(SimulationLifecycleEventType t) {lifecycleListenerType = t;}

  public:
    /** @name Constructors, destructor */
    //@{
    /**
     * Error is identified by an error code, and the message comes from a
     * string table. The error string may expect printf-like arguments
     * (%s, %d) which also have to be passed to the constructor.
     */
    cException(ErrorCodeInt errcode,...);

    /**
     * To be called like printf(). The error code is set to E_CUSTOM.
     */
    _OPP_GNU_ATTRIBUTE(format(printf, 2, 3))
    cException(const char *msg,...);

    /**
     * Error is identified by an error code, and the message comes from a
     * string table. The error string may expect printf-like arguments
     * (%s, %d) which also have to be passed to the constructor.
     * The 1st arg is the object where the error occurred: its class and
     * object name will be prepended to the message like this: "(cArray)arr".
     */
    cException(const cObject *where, ErrorCodeInt errcode,...);

    /**
     * To be called like printf(). The error code is set to E_CUSTOM.
     * The 1st arg is the object where the error occurred: its class and
     * object name will be prepended to the message like this: "(cArray)arr".
     */
    _OPP_GNU_ATTRIBUTE(format(printf, 3, 4))
    cException(const cObject *where, const char *msg,...);

    /**
     * Copy constructor. We unfortunately need to copy exception objects when
     * handing them back from an activity() method.
     */
    cException(const cException&);

    /**
     * Destructor
     */
    virtual ~cException();

    /**
     * Assignment operator.
     */
    cException& operator=(const cException& other);

    /**
     * Creates and returns an exact copy of this object. We unfortunately need
     * to copy exception objects when handing them back from an activity() method.
     */
    virtual cException *dup() const override {return new cException(*this);}
    //@}

    /** @name Updating the exception message */
    //@{
    /**
     * Overwrites the message text with the given one.
     */
    virtual void setMessage(const char *txt) {msg = txt;}

    /**
     * Prefixes the message with the given text and a colon.
     */
    virtual void prependMessage(const char *txt) {msg = std::string(txt) + ": " + msg;}

    /**
     * Adds a nested exception.
     */
    virtual void addNestedException(std::exception& e);

    /**
     * Adds a nested exception.
     */
    virtual void addNestedException(cRuntimeError& e);
    //@}

    /** @name Getting exception info */
    //@{
    /**
     * Whether the exception represents an error, or a non-error condition
     * such as warning or normal termination (e.g. "Simulation completed").
     */
    virtual bool isError() const {return false;}

    /**
     * Delegates to e.isError() if e is a cException, and returns true otherwise.
     */
    static bool isError(std::exception& e);

    /**
     * Returns the error code.
     */
    virtual int getErrorCode() const {return errorCode;}

    /**
     * Returns the text of the error. Redefined from std::exception.
     */
    virtual const char *what() const throw() override {return msg.c_str();}

    /**
     * Returns a formatted message that includes the "Error" word, context
     * information, the event number and simulation time if available
     * and relevant, in addition to the exception message (what()).
     */
    virtual std::string getFormattedMessage(bool addPrefix=true) const;

    /**
     * Delegates to e.getFormattedMessage() if e is a cException, and returns e.what() otherwise.
     */
    static std::string getFormattedMessage(std::exception& e, bool addPrefix=true);

    /**
     * Returns in which stage of the simulation the exception object was created:
     * during network building (STAGE_BUILD), network initialization (STAGE_INITIALIZE),
     * simulation execution (STAGE_EVENT), finalization (STAGE_FINISH), or network
     * cleanup (STAGE_CLEANUP).
     */
    virtual int getSimulationStage() const {return simulationStage;}

    /**
     * Returns the event number at the creation of the exception object.
     */
    virtual eventnumber_t getEventNumber() const {return eventNumber;}

    /**
     * Returns the simulation time at the creation of the exception object.
     */
    virtual simtime_t getSimtime() const {return simtime;}

    /**
     * Returns true if the exception has "context info", that is, it occurred
     * within a known module or channel. getContextClassName(), getContextFullPath()
     * and getContextComponentId() may only be called if this method returns true.
     */
    virtual bool hasContext() const {return hasContext_;}

    /**
     * Returns the class name (NED type name) of the component in context when the
     * exception occurred.
     */
    virtual const char *getContextClassName() const {return contextClassName.c_str();}

    /**
     * Returns the full path of the component in context when the exception
     * occurred.
     */
    virtual const char *getContextFullPath() const {return contextFullPath.c_str();}

    /**
     * Returns the ID of the component in context when the exception occurred,
     * or -1 if there was no component in context. The component may not exist
     * any more when the exception is caught (ie. if the exception occurs
     * during network setup, the network is cleaned up immediately).
     */
    virtual int getContextComponentId() const {return contextComponentId;}

    /**
     * Returns the kind of the component in context when the exception
     * occurred. (The return value can be safely cast to cComponent::ComponentKind.)
     */
    virtual int getContextComponentKind() const {return contextComponentKind;}

    /**
     * If the exception occurred within a simulation lifecycle listener
     * (cISimulationLifecycleListener), returns the lifecycle event type.
     * Otherwise, it returns -1.
     */
    virtual SimulationLifecycleEventType getLifecycleListenerType() const {return lifecycleListenerType;}

    /**
     * Returns the list of nested exceptions. Nested exceptions are exceptions
     * that were thrown while handling this exception.
     */
    const std::vector<cRuntimeError*>& getNestedExceptions() const {return nestedExceptions;}
    //@}
};

/**
 * @brief Thrown when the simulation is completed without error.
 *
 * For example, cSimpleModule::endSimulation() throws this exception.
 * Statistical result collection objects may also throw this exception
 * to signal that the accuracy of simulation results has reached the
 * desired level.
 *
 * @ingroup Fundamentals
 */
class SIM_API cTerminationException : public cException
{
  public:
    /**
     * Error is identified by an error code, and the message comes from a
     * string table. The error string may expect printf-like arguments
     * (%s, %d) which also have to be passed to the constructor.
     */
    cTerminationException(ErrorCodeInt errcode,...);

    /**
     * To be called like printf(). The error code is set to E_CUSTOM.
     */
    _OPP_GNU_ATTRIBUTE(format(printf, 2, 3))
    cTerminationException(const char *msg,...);

    /**
     * Copy constructor. We unfortunately need to copy exception objects
     * when handing them back from an activity() method.
     */
    cTerminationException(const cTerminationException& e)  = default;

    /**
     * Creates and returns an exact copy of this object. We unfortunately need
     * to copy exception objects when handing them back from an activity() method.
     */
    virtual cTerminationException *dup() const override {return new cTerminationException(*this);}

    /**
     * Termination exceptions are generally not errors, but messages like
     * "Simulation completed".
     */
    virtual bool isError() const override {return false;}
};

/**
 * @brief Thrown when the simulation kernel or other components detect a
 * runtime error.
 *
 * For example, cSimpleModule::scheduleAt() throws this exception when
 * the specified simulation time is in the past, or the message pointer
 * is nullptr.
 *
 * @ingroup Fundamentals
 */
class SIM_API cRuntimeError : public cException
{
  public:
    // internal
    bool displayed = false;
  protected:
    // internal
    void notifyEnvir();

  public:

    /**
     * Error is identified by an error code, and the message comes from a
     * string table. The error string may expect printf-like arguments
     * (%s, %d) which also have to be passed to the constructor.
     */
    cRuntimeError(ErrorCodeInt errcode,...);

    /**
     * To be called like printf(). The error code is set to E_CUSTOM.
     */
    _OPP_GNU_ATTRIBUTE(format(printf, 2, 3))
    cRuntimeError(const char *msg,...);

    /**
     * Error is identified by an error code, and the message comes from a
     * string table. The error string may expect printf-like arguments
     * (%s, %d) which also have to be passed to the constructor.
     * The 1st arg is the object where the error occurred: its class and
     * object name will be prepended to the message like this: "(cArray)arr".
     */
    cRuntimeError(const cObject *where, ErrorCodeInt errcode,...);

    /**
     * To be called like printf(). The error code is set to E_CUSTOM.
     * The 1st arg is the object where the error occurred: its class and
     * object name will be prepended to the message like this: "(cArray)arr".
     */
    _OPP_GNU_ATTRIBUTE(format(printf, 3, 4))
    cRuntimeError(const cObject *where, const char *msg,...);

    /**
     * Constructor for re-throwing an exception with location info.
     */
    cRuntimeError(const std::exception& e, const char *location=nullptr);

    /**
     * Copy constructor. We unfortunately need to copy exception objects when
     * handing them back from an activity() method.
     */
    cRuntimeError(const cRuntimeError& e) : cException(e) {}

    /**
     * Creates and returns an exact copy of this object. We unfortunately need to copy
     * exception objects when handing them back from an activity() method.
     */
    virtual cRuntimeError *dup() const override {return new cRuntimeError(*this);}

    /**
     * Overridden to return true.
     */
    virtual bool isError() const override {return true;}

};

/**
 * @brief Thrown from deleteModule() when the active activity() module is
 * about to be deleted, in order to exit that module immediately.
 *
 * @ingroup Internals
 */
class SIM_API cDeleteModuleException : public cException
{
  protected:
    cModule *toDelete;

  public:
    /**
     * Default ctor.
     */
    cDeleteModuleException(cModule *toDelete) : cException(), toDelete(toDelete) {}

    /**
     * Copy constructor. We unfortunately need to copy exception objects when
     * handing them back from an activity() method.
     */
    cDeleteModuleException(const cDeleteModuleException& e)  = default;

    /**
     * Creates and returns an exact copy of this object. We unfortunately need to copy
     * exception objects when handing them back from an activity() method.
     */
    virtual cDeleteModuleException *dup() const override {return new cDeleteModuleException(*this);}

    /**
     * Returns the module to delete.
     */
    virtual cModule *getModuleToDelete() const {return toDelete;}

    /**
     * This exception type does not represent an error condition.
     */
    virtual bool isError() const override {return false;}
};

/**
 * @brief Used internally when deleting an activity() simple module.
 *
 * Then, the coroutine running activity() is "asked" to throw a
 * cStackCleanupException to achieve stack unwinding, a side effect of
 * exceptions, in order to properly clean up activity()'s local variables.
 *
 * @ingroup Internals
 */
class SIM_API cStackCleanupException : public cException
{
  public:
    /**
     * Default ctor.
     */
    cStackCleanupException() : cException() {}

    /**
     * Copy constructor. We unfortunately need to copy exception objects when
     * handing them back from an activity() method.
     */
    cStackCleanupException(const cStackCleanupException& e)  = default;

    /**
     * Creates and returns an exact copy of this object. We unfortunately need to
     * copy exception objects when handing them back from an activity() method.
     */
    virtual cStackCleanupException *dup() const override {return new cStackCleanupException(*this);}

    /**
     * This exception type does not represent an error condition.
     */
    virtual bool isError() const override {return false;}
};

}  // namespace omnetpp


#endif


