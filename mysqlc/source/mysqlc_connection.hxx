/*************************************************************************
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* 
* Copyright 2008 by Sun Microsystems, Inc.
*
* OpenOffice.org - a multi-platform office productivity suite
*
* $RCSfile: mysqlc_connection.hxx,v $
*
* $Revision: 1.1.2.4 $
*
* This file is part of OpenOffice.org.
*
* OpenOffice.org is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License version 3
* only, as published by the Free Software Foundation.
*
* OpenOffice.org is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU Lesser General Public License version 3 for more details
* (a copy is included in the LICENSE file that accompanied this code).
*
* You should have received a copy of the GNU Lesser General Public License
* version 3 along with OpenOffice.org.  If not, see
* <http://www.openoffice.org/license.html>
* for a copy of the LGPLv3 License.
************************************************************************/

#ifndef MYSQLC_CONNECTION_HXX
#define MYSQLC_CONNECTION_HXX

#include "mysqlc_subcomponent.hxx"
#include "mysqlc_types.hxx"

#include <boost/shared_ptr.hpp>
#include <com/sun/star/beans/PropertyValue.hpp>
#include <com/sun/star/lang/DisposedException.hpp>
#include <com/sun/star/lang/XServiceInfo.hpp>
#include <com/sun/star/lang/XUnoTunnel.hpp>
#include <com/sun/star/sdbc/ColumnSearch.hpp>
#include <com/sun/star/sdbc/ColumnValue.hpp>
#include <com/sun/star/sdbc/DataType.hpp>
#include <com/sun/star/sdbc/SQLWarning.hpp>
#include <com/sun/star/sdbc/XConnection.hpp>
#include <com/sun/star/sdbc/XWarningsSupplier.hpp>
#include <com/sun/star/util/XStringSubstitution.hpp>

#include <tools/preextstl.h>
#include <cppconn/driver.h>
#include <tools/postextstl.h>
#include <cppuhelper/compbase3.hxx>
#include <cppuhelper/weakref.hxx>
#include <rtl/string.hxx>

#include <map>

#define UNUSED_PARAM __attribute__((unused))

namespace sql
{
    class SQLException;
}

namespace connectivity
{
    class OMetaConnection;
    class ODatabaseMetaData;

    namespace mysqlc
    {
        using ::rtl::OUString;
        using ::com::sun::star::sdbc::SQLWarning;
        using ::com::sun::star::sdbc::SQLException;
        using ::com::sun::star::uno::RuntimeException;
        typedef ::com::sun::star::uno::Reference< ::com::sun::star::sdbc::XStatement > my_XStatementRef;
        typedef ::com::sun::star::uno::Reference< ::com::sun::star::sdbc::XPreparedStatement > my_XPreparedStatementRef;
        typedef ::com::sun::star::uno::Reference< ::com::sun::star::container::XNameAccess > my_XNameAccessRef;
        typedef ::com::sun::star::uno::Reference< ::com::sun::star::sdbc::XDatabaseMetaData > my_XDatabaseMetaDataRef;

        typedef ::cppu::WeakComponentImplHelper3<	::com::sun::star::sdbc::XConnection,
                                                    ::com::sun::star::sdbc::XWarningsSupplier,
                                                    ::com::sun::star::lang::XServiceInfo
                                                > OMetaConnection_BASE;
        struct ConnectionSettings
        {
            rtl_TextEncoding encoding;
            std::auto_ptr<sql::Connection> cppConnection;
            OUString schema;
            OUString quoteIdentifier;
            OUString connectionURL;
            sal_Bool readOnly;
        };

        class MysqlCDriver;

        typedef OMetaConnection_BASE OConnection_BASE;

        typedef std::vector< ::com::sun::star::uno::WeakReferenceHelper > OWeakRefArray;

        class OConnection : public OBase_Mutex,
                            public OConnection_BASE,
                            public connectivity::mysqlc::OSubComponent<OConnection, OConnection_BASE>
        {
            friend class connectivity::mysqlc::OSubComponent<OConnection, OConnection_BASE>;

        private:
            ConnectionSettings  m_settings;

        private:
            ::com::sun::star::uno::Reference< com::sun::star::container::XNameAccess > m_typeMap;
            ::com::sun::star::uno::Reference< com::sun::star::util::XStringSubstitution > m_xParameterSubstitution;
        protected:
            
            //====================================================================
            // Data attributes
            //====================================================================
            ::com::sun::star::uno::WeakReference< ::com::sun::star::sdbc::XDatabaseMetaData > m_xMetaData;

            OWeakRefArray	m_aStatements;	// vector containing a list
                                            // of all the Statement objects
                                            // for this Connection

            SQLWarning	    m_aLastWarning;	// Last SQLWarning generated by an operation
            OUString	    m_aURL;			// URL of connection
            OUString	    m_sUser;		// the user name
            MysqlCDriver&   m_rDriver;	    // Pointer to the owning driver object
            sql::Driver*    cppDriver;

            sal_Bool	m_bClosed;
            sal_Bool	m_bUseCatalog;	// should we use the catalog on filebased databases
            sal_Bool	m_bUseOldDateFormat;


            void		buildTypeInfo() throw(SQLException);
        public:
            OUString getMysqlVariable(const char *varname)
                                                                throw(SQLException, RuntimeException);

            sal_Int32 getMysqlVersion() 
                                                                throw(SQLException, RuntimeException);

            virtual void construct(const OUString& url,const ::com::sun::star::uno::Sequence< ::com::sun::star::beans::PropertyValue >& info)
                                                                throw(SQLException);

            OConnection(MysqlCDriver& _rDriver, sql::Driver * cppDriver);
            virtual ~OConnection();

            void closeAllStatements ()							throw(SQLException);


            rtl_TextEncoding getConnectionEncoding() { return m_settings.encoding; }


            // OComponentHelper
            virtual void SAL_CALL disposing(void);

            // XInterface
            virtual void SAL_CALL release()						throw();

            // XServiceInfo
            DECLARE_SERVICE_INFO();
            // XConnection
            my_XStatementRef SAL_CALL createStatement()
                                                                throw(SQLException, RuntimeException);

            my_XPreparedStatementRef SAL_CALL prepareStatement(const OUString& sql)
                                                                throw(SQLException, RuntimeException);

            my_XPreparedStatementRef SAL_CALL prepareCall(const OUString& sql)
                                                                throw(SQLException, RuntimeException);

            OUString SAL_CALL nativeSQL(const OUString& sql)
                                                                throw(SQLException, RuntimeException);

            void SAL_CALL setAutoCommit(sal_Bool autoCommit)
                                                                throw(SQLException, RuntimeException);

            sal_Bool SAL_CALL getAutoCommit()
                                                                throw(SQLException, RuntimeException);

            void SAL_CALL commit()
                                                                throw(SQLException, RuntimeException);

            void SAL_CALL rollback()
                                                                throw(SQLException, RuntimeException);

            sal_Bool SAL_CALL isClosed()
                                                                throw(SQLException, RuntimeException);

            my_XDatabaseMetaDataRef SAL_CALL getMetaData()
                                                                throw(SQLException, RuntimeException);

            void SAL_CALL setReadOnly(sal_Bool readOnly)
                                                                throw(SQLException, RuntimeException);

            sal_Bool SAL_CALL isReadOnly()
                                                                throw(SQLException, RuntimeException);

            void SAL_CALL setCatalog(const OUString& catalog)
                                                                throw(SQLException, RuntimeException);

            OUString SAL_CALL getCatalog()
                                                                throw(SQLException, RuntimeException);

            void SAL_CALL setTransactionIsolation(sal_Int32 level)
                                                                throw(SQLException, RuntimeException);

            sal_Int32 SAL_CALL getTransactionIsolation()
                                                                throw(SQLException, RuntimeException);

            my_XNameAccessRef SAL_CALL getTypeMap()
                                                                throw(SQLException, RuntimeException);

            void SAL_CALL setTypeMap(const my_XNameAccessRef& typeMap)
                                                                throw(SQLException, RuntimeException);
            // XCloseable
            void SAL_CALL close()
                                                                throw(SQLException, RuntimeException);
            // XWarningsSupplier
            ::com::sun::star::uno::Any SAL_CALL getWarnings()
                                                                throw(SQLException, RuntimeException);
            void SAL_CALL clearWarnings()
                                                                throw(SQLException, RuntimeException);

            // TODO: Not used
            //sal_Int32 sdbcColumnType(OUString typeName);
            inline const ConnectionSettings& getConnectionSettings() const { return m_settings; }
            ::rtl::OUString transFormPreparedStatement(const ::rtl::OUString& _sSQL);
            
            // should we use the catalog on filebased databases
            inline sal_Bool			    isCatalogUsed()     const { return m_bUseCatalog; }
            inline OUString			    getUserName()       const { return m_sUser; }
            inline const MysqlCDriver&  getDriver()			const { return m_rDriver;}
            inline rtl_TextEncoding	    getTextEncoding()	const { return m_settings.encoding; }

        }; /* OConnection */
        // TODO: Not used.
        //inline OUString getPattern(OUString p) { return (p.getLength()) ? p : ASC2OU("%"); }
    } /* mysqlc */
} /* connectivity */
#endif // MYSQLC_CONNECTION_HXX

/*
 * Local variables:
 * tab-width: 4
 * c-basic-offset: 4
 * End:
 * vim600: noet sw=4 ts=4 fdm=marker
 * vim<600: noet sw=4 ts=4
 */

